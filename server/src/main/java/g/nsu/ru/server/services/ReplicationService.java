package g.nsu.ru.server.services;

import g.nsu.ru.server.model.operations.Operation;
import g.nsu.ru.server.model.operations.OperationsLogInMemory;
import g.nsu.ru.server.node.Peer;
import g.nsu.ru.server.node.RaftNode;
import g.nsu.ru.server.timer.ResetElectionTimerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static g.nsu.ru.server.model.State.FOLLOWER;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplicationService {

    private final RestTemplate restTemplate;

    private final RaftNode raftNode;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OperationsLogInMemory operationsLog;



    private List<AnswerAppendDTO> sendAppendToAllPeers(List<Integer> peers) {
        List<CompletableFuture<AnswerAppendDTO>> answerFutureList =
                peers.stream()
                        .map(this::sendAppendForOnePeer)
                        .collect(Collectors.toList());

        return CompletableFuture.allOf(
                answerFutureList.toArray(new CompletableFuture[0])
        ).thenApply(v ->
                answerFutureList.stream().map(CompletableFuture::join).collect(Collectors.toList())
        ).join();
    }

    private CompletableFuture<AnswerAppendDTO> sendAppendForOnePeer(Integer id) {
        return CompletableFuture.supplyAsync(() -> {
            String opNameForLog = "Heartbeat";
            try {

                Peer peer = raftNode.getPeer(id);

                // Если индекс последней операции ≥ nextIndex для follower: отправить
                // AppendEntries RPC с операциями, начиная с nextIndex
                Operation operation;
                Integer prevIndex;
                if (peer.getNextIndex() <= operationsLog.getLastIndex()) {
                    opNameForLog = "Append";
                    log.info("Узел #{} отправляет {} запрос узлу {}. Следующий индекс узла: {}. Последний индекс лога: {}",
                            raftNode.getId(), opNameForLog, id, peer.getNextIndex(), operationsLog.getLastIndex());
                    operation = operationsLog.get(peer.getNextIndex());
                    prevIndex = peer.getNextIndex() - 1;
                } else {
                    operation = null;
                    log.debug("Узел #{} отправляет {} запрос узлу {}", raftNode.getId(), opNameForLog, id);
                    prevIndex = operationsLog.getLastIndex();
                }

                RequestAppendDTO requestAppendDTO = new RequestAppendDTO(
                        raftNode.getCurrentTerm(),
                        raftNode.getId(),
                        prevIndex,
                        operationsLog.getTerm(prevIndex),
                        raftNode.getCommitIndex(),
                        operation
                );

                ResponseEntity<AnswerAppendDTO> response = callPost(id.toString(), requestAppendDTO);

                return Optional.ofNullable(response.getBody()).orElse(new AnswerAppendDTO(id, NO_CONTENT));
            } catch (ResourceAccessException e) {
                log.error("Узел #{} ошибка {} запроса узлу {}. {} {}", raftNode.getId(), opNameForLog, id, e.getClass(), e.getMessage());
                return new AnswerAppendDTO(id, SERVICE_UNAVAILABLE);
            } catch (Exception e) {
                log.error(String.format("Узел # %d ошибка %s запроса узлу %d", raftNode.getId(), opNameForLog, id), e);
                return new AnswerAppendDTO(id, INTERNAL_SERVER_ERROR);
            }
        });
    }

    public void appendRequest() {
        log.debug("Узел #{} отправляет запросы", raftNode.getId());
        List<Integer> peersIds = raftNode.getPeers().stream().map(Peer::getId).collect(Collectors.toList());

        while (!peersIds.isEmpty()) { // Повторяем, пока не получены успешные ответы от всех узлов
            List<AnswerAppendDTO> answers = sendAppendToAllPeers(peersIds);
            peersIds = new ArrayList<>();
            for (AnswerAppendDTO answer : answers) {
                if (answer.getStatusCode().equals(OK)) {
                    if (answer.getTerm() > raftNode.getCurrentTerm()) {
                        raftNode.setTermGreaterThenCurrent(answer.getTerm());
                        return;
                    }
                    Peer peer = raftNode.getPeer(answer.getId());
                    if (answer.getSuccess()) {
                        log.debug("Узел #{} получил \"успех запроса\" от узла {}", raftNode.getId(), answer.getId());
                        log.debug("Узел #{} устанавливает следующий индекс для узла {}: следующий: {} совпадающий: {}. Предыдущий совпадающий: {}",
                                raftNode.getId(), answer.getId(), answer.getMatchIndex() + 1, answer.getMatchIndex(), peer.getMatchIndex());
                        peer.setNextIndex(answer.getMatchIndex() + 1);
                        peer.setMatchIndex(answer.getMatchIndex());
                        if (peer.getNextIndex() <= operationsLog.getLastIndex())
                            peersIds.add(answer.getId());
                    } else {
                        log.debug("Узел #{} запрос отклонен узлом {}, уменьшает текущий следующий индекс {}",
                                raftNode.getId(), answer.getId(), peer.getNextIndex());
                        peer.decNextIndex();
                        peersIds.add(answer.getId());
                    }
                }
            }
            tryToCommit();
        }
    }

    private void tryToCommit() {
        log.debug("Узел #{} пытается зафиксировать операцию. Текущий индекс : {}", raftNode.getId(), raftNode.getCommitIndex());
        while (true) {
            int N = raftNode.getCommitIndex() + 1;

            Supplier<Long> count = () ->
                    raftNode.getPeers().stream().map(Peer::getMatchIndex)
                            .filter(matchIndex -> matchIndex >= N).count() + 1;

            if (operationsLog.getLastIndex() >= N &&
                    operationsLog.getTerm(N).equals(raftNode.getCurrentTerm()) &&
                    count.get() >= raftNode.getQuorum()) {
                raftNode.setCommitIndex(N);
            } else
                return;
        }
    }


    public AnswerAppendDTO append(RequestAppendDTO dto) {

        raftNode.cancelIfNotActive();

        // Reply false if term < currentTerm (§5.1)
        if (dto.getTerm() < raftNode.getCurrentTerm()) {
            log.info("Peer #{} Rejected request from {}. Term {} too small", raftNode.getId(), dto.getLeaderId(),
                    dto.getTerm());
            return new AnswerAppendDTO(raftNode.getId(), raftNode.getCurrentTerm(), false, null);
        } else if (dto.getTerm() > raftNode.getCurrentTerm()) {
            //If RPC request or response contains term T > currentTerm: set currentTerm = T,
            raftNode.setCurrentTerm(dto.getTerm());
            raftNode.setVotedFor(null);
        }
        applicationEventPublisher.publishEvent(new ResetElectionTimerEvent(this));
        // convert to follower. Just one Leader RULE
        if (!raftNode.getState().equals(FOLLOWER)) {
            raftNode.setState(FOLLOWER);
        }

//        2. Reply false if operations does not contain an entry at prevLogIndex
//        whose term matches prevLogTerm (§5.3)
        if ((dto.getPrevLogIndex() > operationsLog.getLastIndex()) ||
                !dto.getPrevLogTerm().equals(operationsLog.getTerm(dto.getPrevLogIndex()))) {
            log.info(
                    "Peer #{} Rejected request from {}. Log doesn't contain prev term. Current term {}, Candidate term {} ",
                    raftNode.getId(), dto.getLeaderId(), raftNode.getCurrentTerm(), dto.getTerm());
            return new AnswerAppendDTO(raftNode.getId(), raftNode.getCurrentTerm(), false, null);
        }


        String opNameForLog = "heartbeat";
        Operation newOperation = dto.getOperation();
        if (newOperation != null) {

            opNameForLog = "append";
            int newOperationIndex = dto.getPrevLogIndex() + 1;
            log.info("Peer #{} checking new operation. New index {}. Operation term: {}. Last index: {} ",
                    raftNode.getId(), newOperationIndex, newOperation.getTerm(), operationsLog.getLastIndex());

            synchronized (this) {
//              3. If an existing entry conflicts with a new one (same index but different terms),
//              delete the existing entry and all that follow it (§5.3)
                if ((newOperationIndex <= operationsLog.getLastIndex()) &&
                        (!newOperation.getTerm().equals(operationsLog.getTerm(newOperationIndex)))) {
                    operationsLog.removeAllFromIndex(newOperationIndex);
                }
//        4. Append any new entries not already in the operations
                if (newOperationIndex <= operationsLog.getLastIndex())
                {
                    //don't need to append
                    return new AnswerAppendDTO(raftNode.getId(), raftNode.getCurrentTerm(), true, operationsLog.getLastIndex());
                }
                log.info("Peer #{} Append new operation. {}. key:{} val:{}",
                        raftNode.getId(), newOperation.getType(), newOperation.getEntry().getKey(),
                        newOperation.getEntry().getVal());
                operationsLog.append(newOperation);
            }
        }
//        5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if (dto.getLeaderCommit() > raftNode.getCommitIndex()) {
            raftNode.setCommitIndex(Math.min(dto.getLeaderCommit(), operationsLog.getLastIndex()));
        }

        log.debug("Peer #{}. Success answer to {} request. Term: {}. Mach index {}", raftNode.getId(), opNameForLog,
                raftNode.getCurrentTerm(), operationsLog.getLastIndex());
        return new AnswerAppendDTO(raftNode.getId(), raftNode.getCurrentTerm(), true, operationsLog.getLastIndex());
    }

    public ResponseEntity<AnswerAppendDTO> callPost(String id,
                                                    RequestAppendDTO requestAppendDTO) {

        String url = "http://localhost:800" + id + "/replication/append";
        HttpHeaders headers = new HttpHeaders();

        HttpEntity<RequestAppendDTO> request = new HttpEntity<>(requestAppendDTO, headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, AnswerAppendDTO.class, id);
    }

}
