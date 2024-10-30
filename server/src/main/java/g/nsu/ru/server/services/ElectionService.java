package g.nsu.ru.server.services;


import g.nsu.ru.server.model.election.AnswerVoteDTO;
import g.nsu.ru.server.model.election.RequestVoteDTO;
import g.nsu.ru.server.model.operations.OperationsLogInMemory;
import g.nsu.ru.server.node.Peer;
import g.nsu.ru.server.node.RaftNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static g.nsu.ru.server.model.State.*;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionService {

    private static final int VOTE_RETRY_DELAY = 2000;
    private final RaftNode raftNode;
    private final RestTemplate restTemplate = new RestTemplate();
    private final OperationsLogInMemory operationsLog;


    private CompletableFuture<AnswerVoteDTO> getVoteFromOnePeer(Integer id,
                                                                Long term) {
        return CompletableFuture.supplyAsync(() -> {
            if (!checkCurrentElectionStatus(term))
                return new AnswerVoteDTO(id, NO_CONTENT);
            try {
                log.info("Peer #{} Send vote request to {}", raftNode.getId(), id);

                RequestVoteDTO requestVoteDTO = new RequestVoteDTO(term, raftNode.getId(),
                        operationsLog.getLastIndex(),
                        operationsLog.getLastTerm());
                ResponseEntity<AnswerVoteDTO> response = callPost(id.toString(), requestVoteDTO);

                return Optional.ofNullable(response.getBody()).
                        orElse(new AnswerVoteDTO(id, NO_CONTENT));
            } catch (Exception e) {
                log.error("Peer #{} Vote request error for {}. {} {} ", raftNode.getId(), id, e.getClass(),
                        e.getMessage());
                return new AnswerVoteDTO(id, INTERNAL_SERVER_ERROR);
            }

        });
    }

    private List<AnswerVoteDTO> getVoteFromAllPeers(Long term,
                                                    List<Integer> peers) {
        log.debug("Peer #{} Forward vote request to peers. Term {}. Peers count: {}", raftNode.getId(), term,
                peers.size());
        List<CompletableFuture<AnswerVoteDTO>> answerFutureList =
                peers.stream()
                        .map(i -> getVoteFromOnePeer(i, term))
                        .collect(Collectors.toList());

        if (checkCurrentElectionStatus(term)) {
            return CompletableFuture.allOf(
                    answerFutureList.toArray(new CompletableFuture[0])
            ).thenApply(v ->
                    answerFutureList.stream().map(CompletableFuture::join).collect(Collectors.toList())
            ).join();
        } else
            return new ArrayList<>();
    }


    public void processElection() {
        if (raftNode.getState().equals(LEADER) || !raftNode.getActive()) {
            return;
        }

        log.info("Узел #{} начинает выборы в терме {}", raftNode.getId(), raftNode.getCurrentTerm());

        raftNode.setState(CANDIDATE);
        Long term = raftNode.incCurrentTerm();
        raftNode.setVotedFor(raftNode.getId());

        List<Integer> peersIds = raftNode.getPeers().stream().map(Peer::getId).collect(Collectors.toList());
        long voteGrantedCount = 1L;
        long voteRevokedCount = 0L;

        // Пока узел находится в статусе кандидата и еще идет текущий термин
        while (checkCurrentElectionStatus(term)) {
            List<AnswerVoteDTO> answers = getVoteFromAllPeers(term, peersIds);
            peersIds = new ArrayList<>();
            for (AnswerVoteDTO answer : answers) {
                if (answer.getStatusCode().equals(OK)) {
                    // Если термин узла больше текущего термина, сбросить статус и вернуться к follower
                    if (answer.getTerm() > raftNode.getCurrentTerm()) {
                        raftNode.setTermGreaterThenCurrent(answer.getTerm());
                        return;
                    }
                    if (answer.isVoteGranted()) {
                        log.info("Узел #{} получил голос от {}", raftNode.getId(), answer.getId());
                        raftNode.getPeer(answer.getId()).setVoteGranted(true);
                        voteGrantedCount++;
                    } else
                        log.info("Узел #{} голос отклонен от {}", raftNode.getId(), answer.getId());
                    voteRevokedCount++;
                } else {
                    log.info("Узел #{} не получил ответа на запрос голосования от {}", raftNode.getId(), answer.getId());
                    peersIds.add(answer.getId());
                }
            }
            // Проверяем, есть ли кворум голосов "за" для выигрыша выборов
            if (voteGrantedCount >= raftNode.getQuorum()) {
                becomeLeader(term);
                return;
            } else if (voteRevokedCount >= raftNode.getQuorum()) {
                loseElection(term);
                return;
            }
            // Если кворум не достигнут и голосование не завершено, подождать и повторить
            delay();
        }
    }

    private void delay() {
        try {
            log.info("Peer #{} Preparing to retry vote request", raftNode.getId());
            Thread.sleep(VOTE_RETRY_DELAY);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    private boolean checkCurrentElectionStatus(Long term) {
        return term.equals(raftNode.getCurrentTerm()) && raftNode.getState().equals(CANDIDATE);
    }

    private void becomeLeader(Long term) {
        if (checkCurrentElectionStatus(term)) {
            log.info("\u001B[32mNode {} стал ЛИДЕРОМ в терме {}\u001B[0m", raftNode.getId(), term);
            raftNode.setState(LEADER);

            raftNode.getPeers().forEach(peer ->
                    peer.setNextIndex(operationsLog.getLastIndex() + 1)
            );
        }

    }

    private void loseElection(Long term) {
        if (checkCurrentElectionStatus(term)) {
            log.info("Нода #{} проиграла выборы", raftNode.getId());
            raftNode.setState(FOLLOWER);
        }
    }


    public AnswerVoteDTO vote(RequestVoteDTO dto) {
        raftNode.cancelIfNotActive();
        log.info("Узел #{} получил запрос на голосование от {} с термином {}. Текущий термин: {}. Проголосовал за: {}",
                raftNode.getId(),
                dto.getCandidateId(),
                dto.getTerm(),
                raftNode.getCurrentTerm(),
                raftNode.getVotedFor());

        boolean termCheck;
        if (dto.getTerm() < raftNode.getCurrentTerm()) {
            return new AnswerVoteDTO(raftNode.getId(), raftNode.getCurrentTerm(), false);
        } else if (dto.getTerm().equals(raftNode.getCurrentTerm())) {
            termCheck = (raftNode.getVotedFor() == null || raftNode.getVotedFor().equals(dto.getCandidateId()));
        } else {
            termCheck = true;
            raftNode.setTermGreaterThenCurrent(dto.getTerm());
        }

        boolean logCheck = !((operationsLog.getLastTerm() > dto.getLastLogTerm()) ||
                ((operationsLog.getLastTerm().equals(dto.getLastLogTerm())) &&
                        (operationsLog.getLastIndex() > dto.getLastLogIndex())));

        boolean voteGranted = termCheck && logCheck;

        if (voteGranted) {
            raftNode.setVotedFor(dto.getCandidateId());
            log.info("Узел #{} отдал голос за {}", raftNode.getId(), dto.getCandidateId());
        } else {
            log.info("Узел #{} отклонил голос за {}. Текущий термин {}, Термин кандидата {}",
                    raftNode.getId(),
                    dto.getCandidateId(),
                    raftNode.getCurrentTerm(), dto.getTerm());
        }
        return new AnswerVoteDTO(raftNode.getId(), raftNode.getCurrentTerm(), voteGranted);
    }



    private ResponseEntity<AnswerVoteDTO> callPost(String id, RequestVoteDTO requestVoteDTO) {
        String url = "http://localhost:800" + id + "/election/vote";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestVoteDTO> requestEntity = new HttpEntity<>(requestVoteDTO, headers);

        return restTemplate.postForEntity(url, requestEntity, AnswerVoteDTO.class);
    }


}
