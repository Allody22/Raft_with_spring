package g.nsu.ru.server.services;

import g.nsu.ru.server.events.ResetElectionTimerEvent;
import g.nsu.ru.server.model.AnswerAppendDTO;
import g.nsu.ru.server.model.RequestAppendDTO;
import g.nsu.ru.server.model.operations.Operation;
import g.nsu.ru.server.model.operations.OperationsLogInMemory;
import g.nsu.ru.server.node.Peer;
import g.nsu.ru.server.node.RaftNode;
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
                        .toList();

        return CompletableFuture.allOf(
                answerFutureList.toArray(new CompletableFuture[0])
        ).thenApply(v ->
                //Вызываем блокировку через join и ждём пока все фьючер таски завершатся
                answerFutureList.stream().map(CompletableFuture::join).collect(Collectors.toList())
        ).join();
    }

    private CompletableFuture<AnswerAppendDTO> sendAppendForOnePeer(Integer id) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                Peer peer = raftNode.getPeer(id);

                Operation operation;
                Integer prevIndex;
                if (peer.getNextIndex() <= operationsLog.getLastIndex()) {
                    operation = operationsLog.get(peer.getNextIndex());
                    prevIndex = peer.getNextIndex() - 1;
                } else {
                    operation = null;
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
//                log.error("Узел #{} ошибка {} запроса узлу {}. {} {}", raftNode.getId(), opNameForLog, id, e.getClass(), e.getMessage());
                return new AnswerAppendDTO(id, SERVICE_UNAVAILABLE);
            } catch (Exception e) {
//                log.error(String.format("Узел %d ошибка %s запроса узлу %d", raftNode.getId(), opNameForLog, id), e);
                return new AnswerAppendDTO(id, INTERNAL_SERVER_ERROR);
            }
        });
    }

    public void appendRequest() {
        List<Integer> peersIds = raftNode.getPeers().stream().map(Peer::getId).collect(Collectors.toList());

        while (!peersIds.isEmpty()) {
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
                        peer.setNextIndex(answer.getMatchIndex() + 1);
                        peer.setMatchIndex(answer.getMatchIndex());
                        raftNode.setVotedFor(answer.getId());
                        if (peer.getNextIndex() <= operationsLog.getLastIndex())
                            peersIds.add(answer.getId());
                    } else {
                        peer.decNextIndex();
                        peersIds.add(answer.getId());
                    }
                }
            }
            tryToCommit();
        }
    }

    private void tryToCommit() {
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
        if (dto.getTerm() < raftNode.getCurrentTerm()) {
            log.info("Узел #{} отклонил запрос от {}. Терм {} меньше чем у узла {}", raftNode.getId(), dto.getLeaderId(),
                    dto.getTerm(), raftNode.getCurrentTerm());
            return new AnswerAppendDTO(raftNode.getId(), raftNode.getCurrentTerm(), false, null);
        } else if (dto.getTerm() > raftNode.getCurrentTerm()) {
            raftNode.setCurrentTerm(dto.getTerm());
            raftNode.setVotedFor(dto.getLeaderId());
        }
        applicationEventPublisher.publishEvent(new ResetElectionTimerEvent(this));
        // Если ты почему-то не фолловер, то ты им станешь
        if (!raftNode.getState().equals(FOLLOWER)) {
            log.info("\u001b[33mУзел {} в аппенде стал фолловером\u001b[o", raftNode.getId());
            raftNode.setState(FOLLOWER);
        }

        if ((dto.getPrevLogIndex() > operationsLog.getLastIndex()) ||
                !dto.getPrevLogTerm().equals(operationsLog.getTerm(dto.getPrevLogIndex()))) {
            log.info("Узел {} не понял запрос от лидера {}. Лог не содержит предыдущий терм. Текущий терм {}, Терм из запроса {} ",
                    raftNode.getId(), dto.getLeaderId(), raftNode.getCurrentTerm(), dto.getTerm());
            return new AnswerAppendDTO(raftNode.getId(), raftNode.getCurrentTerm(), false, null);
        }

        Operation newOperation = dto.getOperation();
        //Если не хартбит
        if (newOperation != null) {

            int newOperationIndex = dto.getPrevLogIndex() + 1;
            log.info("Узел #{} получает новую операцию и индекс. Новый индекс {}. Терм операции: {}. Прошлый индекс: {} ",
                    raftNode.getId(), newOperationIndex, newOperation.getTerm(), operationsLog.getLastIndex());

            synchronized (this) {
//              3. If an existing entry conflicts with a new one (same index but different terms),
//              delete the existing entry and all that follow it (§5.3)
                if ((newOperationIndex <= operationsLog.getLastIndex()) &&
                        (!newOperation.getTerm().equals(operationsLog.getTerm(newOperationIndex)))) {
                    operationsLog.removeAllFromIndex(newOperationIndex);
                }
//        4. Append any new entries not already in the operations
                if (newOperationIndex <= operationsLog.getLastIndex()) {
                    //don't need to append
                    return new AnswerAppendDTO(raftNode.getId(), raftNode.getCurrentTerm(), true, operationsLog.getLastIndex());
                }
                log.info("Узел {} сделал новую операцию: {}. key:{} val:{}",
                        raftNode.getId(), newOperation.getType(), newOperation.getEntry().getKey(),
                        newOperation.getEntry().getVal());
                operationsLog.append(newOperation);
            }
        }
        if (dto.getLeaderCommit() > raftNode.getCommitIndex()) {
            raftNode.setCommitIndex(Math.min(dto.getLeaderCommit(), operationsLog.getLastIndex()));
        }

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
