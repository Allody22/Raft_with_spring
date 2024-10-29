package g.nsu.ru.server;

import g.nsu.ru.server.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Configuration
@Slf4j
@Data
public class RaftNode  {
    // Текущий терм (сколько раунтов голосования прошло)
    private int currentTerm = 0;

    // За кого в текущем раунде отдан голос
    private String votedFor = null;

    // Список логов (действий по факту) у этого узла
    private List<LogEntry> logEntries = new ArrayList<>();

    // В каком мы сейчас статусе
    private NodeState state = NodeState.FOLLOWER;

    // Собственно наша айдишка
    private String nodeId;

    // Список друзей узлов
    private List<String> peers;

    // Состояние хранилища текущей ноды
    private StateMachine stateMachine = new StateMachine();

    private int commitIndex = -1; // Индекс последней зафиксированной записи
    private int lastApplied = -1; // Индекс последней примененной записи

    // Индексы для репликации
    private Map<String, Integer> nextIndex = new ConcurrentHashMap<>();
    private Map<String, Integer> matchIndex = new ConcurrentHashMap<>();

    private String leaderId = null; // Лидер для данного узла

    // Карта соответствия nodeId -> clientPort
    private Map<String, Integer> nodeClientPorts;

    // Executors для репликации и heartbeat
    private ExecutorService replicationExecutor;
    private ScheduledExecutorService heartbeatScheduler;

    private ScheduledExecutorService electionScheduler = Executors.newScheduledThreadPool(1);;

    private final RaftCommunicationService communicationService;


    @Autowired
    public RaftNode(RaftConfig config, RaftCommunicationService communicationService) {
        log.info("config {}", config);
        this.nodeId = Objects.requireNonNull(config.getNodeId(), "Node ID не должен быть null");
        this.peers = Objects.requireNonNull(config.getPeers(), "Peers не должен быть null");
        this.nodeClientPorts = new ConcurrentHashMap<>(Objects.requireNonNull(config.getNodeClientPorts(), "Node client ports не должен быть null"));
        this.nodeClientPorts.put(nodeId, config.getClientPort());

        // Проверяем, что карта `nodeClientPorts` была инициализирована перед вставкой
        if (this.nodeClientPorts.get(nodeId) == null) {
            throw new IllegalArgumentException("Client port должен быть указан для каждого узла");
        }

        this.replicationExecutor = Executors.newFixedThreadPool(peers.size());
        this.communicationService = communicationService;

        // Задержка перед началом выборов
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            log.error("Ошибка при ожидании перед запуском таймера выборов: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        startElectionTimeout();
    }


    public String getLeaderClientUrl() {
        if (leaderId == null) {
            return null;
        }
        // Если узел является лидером, возвращаем URL текущего узла
        if (leaderId.equals(nodeId)) {
            return "http://localhost:" + nodeClientPorts.get(nodeId);
        }
        Integer leaderClientPort = nodeClientPorts.get(leaderId);
        return leaderClientPort != null ? "http://localhost:" + leaderClientPort : null;
    }


    private void startElectionTimeout() {
        int electionTimeout = getRandomElectionTimeout();
        log.debug("Запуск таймера выборов через {} миллисекунд", electionTimeout);
        electionScheduler.schedule(this::startElection, electionTimeout, TimeUnit.MILLISECONDS);
    }


    private int getRandomElectionTimeout() {
        return ThreadLocalRandom.current().nextInt(1500, 3000);
    }



    @Async
    public void startElection() {
        log.info("Узел {} начал выборы в терме {}", nodeId, currentTerm + 1);
        state = NodeState.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;

        int votesGranted = 1; // Свой голос
        int majority = (peers.size() + 1) / 2 + 1;

        List<Future<Boolean>> voteFutures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(peers.size());

        for (String peerUrl : peers) {
            Callable<Boolean> task = () -> {
                RequestVoteRequest request = new RequestVoteRequest(
                        currentTerm,
                        nodeId,
                        logEntries.size() - 1,
                        getLastLogTerm()
                );
                RequestVoteResponse response = communicationService.sendRequestVote(peerUrl, request);
                if (response != null) {
                    if (response.getTerm() > currentTerm) {
                        synchronized (this) {
                            currentTerm = response.getTerm();
                            state = NodeState.FOLLOWER;
                            votedFor = null;
                        }
                        return false;
                    }
                    return response.getVoteGranted();
                }
                return false;
            };
            voteFutures.add(executor.submit(task));
        }

        executor.shutdown();

        for (Future<Boolean> future : voteFutures) {
            try {
                if (future.get()) {
                    votesGranted++;
                }
                if (votesGranted >= majority) {
                    becomeLeader();
                    return;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Ошибка при получении голоса: {}", e.getMessage());
            }
        }

        // Если не получили большинство голосов, возвращаемся в последователи
        state = NodeState.FOLLOWER;
        votedFor = null;
        log.info("Узел {} не получил большинство голосов и переходит в состояние FOLLOWER", nodeId);
        startElectionTimeout();
    }


    private void becomeLeader() {
        state = NodeState.LEADER;
        leaderId = nodeId;

        log.info("\u001B[32mNode {} стал ЛИДЕРОМ в терме {}\u001B[0m", nodeId, currentTerm);

        // Останавливаем таймер выборов
        if (electionScheduler != null && !electionScheduler.isShutdown()) {
            electionScheduler.shutdownNow();
        }

        // Инициализируем heartbeatScheduler
        startHeartbeat();
    }


    public void sendHeartbeats() {
        if (state == NodeState.LEADER) {
            for (String peerUrl : peers) {
                AppendEntriesRequest request = new AppendEntriesRequest(
                        currentTerm,
                        nodeId,
                        logEntries.size() - 1,
                        getLastLogTerm(),
                        Collections.emptyList(),
                        commitIndex
                );
                replicateLog(peerUrl, request);
            }
        }
    }


    @Async
    public void replicateLog(String peerUrl, AppendEntriesRequest request) {
        try {
            AppendEntriesResponse response = communicationService.sendAppendEntries(peerUrl, request);
            if (response != null) {
                if (response.getSuccess()) {
                    int matchIdx = request.getPrevLogIndex() + request.getEntries().size();
                    matchIndex.put(peerUrl, matchIdx);
                    nextIndex.put(peerUrl, matchIdx + 1);

                    // Обновляем commitIndex после успешной репликации
                    updateCommitIndex();
                } else {
                    // Если терм в ответе больше, обновляем текущий терм и становимся фолловерами
                    if (response.getTerm() > currentTerm) {
                        synchronized (this) {
                            currentTerm = response.getTerm();
                            state = NodeState.FOLLOWER;
                            votedFor = null;
                            leaderId = null;
                        }
                    } else {
                        // Уменьшаем nextIndex для повторной попытки репликации
                        int newNextIdx = Math.max(0, nextIndex.getOrDefault(peerUrl, 0) - 1);
                        nextIndex.put(peerUrl, newNextIdx);

                        // Повторяем репликацию с задержкой
                        heartbeatScheduler.schedule(() -> replicateLog(peerUrl, createAppendEntriesRequest(peerUrl, newNextIdx)), 100, TimeUnit.MILLISECONDS);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при репликации лога узлу {}: {}", peerUrl, e.getMessage());
            // Повторяем репликацию с задержкой
            heartbeatScheduler.schedule(() -> replicateLog(peerUrl, request), 500, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void handleClientCommand(Command command) {
        log.info("client command {}", command);
        if (state == NodeState.LEADER) {
            LogEntry entry = new LogEntry(currentTerm, command);
            logEntries.add(entry);
            log.info("Лидер {} добавил запись в лог: {}", nodeId, entry);

            // Репликация лога на всех последователей
            for (String peerUrl : peers) {
                AppendEntriesRequest request = createAppendEntriesRequest(peerUrl, logEntries.size() - 1);
                replicateLog(peerUrl, request);
            }
        } else {
            // Перенаправление к лидеру
            redirectToLeader(command);
        }
    }


    private AppendEntriesRequest createAppendEntriesRequest(String peerUrl, int prevLogIndex) {
        int initialNextIdx = nextIndex.getOrDefault(peerUrl, 0);
        List<LogEntry> entries = logEntries.subList(initialNextIdx, logEntries.size());
        return new AppendEntriesRequest(
                currentTerm,
                nodeId,
                initialNextIdx -1,
                getTermAtIndex(initialNextIdx - 1),
                entries,
                commitIndex
        );
    }

    public synchronized void updateCommitIndex() {
        List<Integer> matchIndexes = new ArrayList<>(matchIndex.values());
        matchIndexes.add(logEntries.size() - 1); // Добавляем индекс лидера

        Collections.sort(matchIndexes);
        int majorityIndex = matchIndexes.get(matchIndexes.size() / 2);

        if (majorityIndex > commitIndex && logEntries.get(majorityIndex).getTerm() == currentTerm) {
            commitIndex = majorityIndex;
            log.info("Node {} обновил commitIndex до {}", nodeId, commitIndex);
            applyLogEntries();
        }
    }

    public synchronized void applyLogEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = logEntries.get(lastApplied);
            try {
                stateMachine.apply(entry.getCommand());
                log.info("Node {} применил запись лога на индексе {}: {}", nodeId, lastApplied, entry.getCommand());
            } catch (Exception e) {
                log.error("Ошибка при применении записи лога на узле {}: {}", nodeId, e.getMessage());
            }
        }
    }

    public synchronized boolean handleAppendEntries(AppendEntriesRequest request) {
//        log.info("appendEntries request: {}", request);
        try {
            // Проверка терма запроса
            if (request.getTerm() < currentTerm) {
                return false;
            }

            // Обновляем терм и переходим в Follower, если запрос содержит более новый терм
            if (request.getTerm() > currentTerm) {
                log.info("Узел {} обновляет терм с {} до {}", nodeId, currentTerm, request.getTerm());
                currentTerm = request.getTerm();
                state = NodeState.FOLLOWER;
                votedFor = null;
                leaderId = request.getLeaderId();
            }

            // Обновляем leaderId, если отличается
            if (!request.getLeaderId().equals(leaderId)) {
                log.info("Узел {} признаёт лидера {}", nodeId, request.getLeaderId());
                leaderId = request.getLeaderId();
            }

            resetElectionTimeout(); // Сброс таймера выборов

            // Проверка предыдущей записи лога
            if (request.getPrevLogIndex() >= 0 && logEntries.size() <= request.getPrevLogIndex()) {
                log.warn("Узел {}: отсутствует запись в логе с индексом {}", nodeId, request.getPrevLogIndex());
                return false;
            }

            if (request.getPrevLogIndex() >= 0 && !Objects.equals(logEntries.get(request.getPrevLogIndex()).getTerm(), request.getPrevLogTerm())) {
                log.warn("Узел {}: несоответствие термов в логе на индексе {}, удаляем конфликтующие записи", nodeId, request.getPrevLogIndex());
                logEntries.subList(request.getPrevLogIndex(), logEntries.size()).clear(); // Удаляем конфликтующие записи
            }

            // Добавляем новые записи из запроса, если они есть
            int index = request.getPrevLogIndex() + 1;
            for (LogEntry entry : request.getEntries()) {
                if (logEntries.size() > index) {
                    logEntries.set(index, entry);
                } else {
                    logEntries.add(entry);
                }
                index++;
            }

            // Обновляем commitIndex
            if (request.getLeaderCommit() > commitIndex) {
                int newCommitIndex = Math.min(request.getLeaderCommit(), logEntries.size() - 1);
                if (newCommitIndex > commitIndex) {
                    commitIndex = newCommitIndex;
                    applyLogEntries();
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Ошибка в handleAppendEntries на узле {}: {}", nodeId, e.getMessage(), e);
            return false;
        }
    }


    public synchronized boolean handleRequestVote(RequestVoteRequest request) {
        if (request.getTerm() < currentTerm) {
            log.info("Узел {} отклоняет запрос голоса от {} из-за устаревшего терма {}", nodeId, request.getCandidateId(), request.getTerm());
            return false;
        }

        if (request.getTerm() > currentTerm) {
            currentTerm = request.getTerm();
            votedFor = null;
            state = NodeState.FOLLOWER;
            leaderId = null;
        } else if (state != NodeState.FOLLOWER) {
            state = NodeState.FOLLOWER;
            log.info("Узел {} переходит в состояние FOLLOWER из-за RequestVote с равным термом", nodeId);
        }

        // Если у этого узла еще есть возможность проголосовать или он уже проголосовал за этого кандидата,
        // и лог кандидата актуален, то голосуем за него.
        if ((votedFor == null || votedFor.equals(request.getCandidateId())) &&
                isLogUpToDate(request.getLastLogIndex(), request.getLastLogTerm())) {
            votedFor = request.getCandidateId();
            resetElectionTimeout();
            log.info("Узел {} голосует за кандидата {} в терме {}", nodeId, request.getCandidateId(), currentTerm);
            return true;
        }

        log.info("Узел {} отклоняет запрос голоса от {} - голос уже отдан за {}", nodeId, request.getCandidateId(), votedFor);
        return false;
    }


    private void redirectToLeader(Command command) {
        if (leaderId != null) {
            // Отправляем команду лидеру
            try {
                communicationService.sendClientCommand(getLeaderClientUrl(), command);
            } catch (IOException e) {
                log.error("Ошибка при перенаправлении команды лидеру {}: {}", leaderId, e.getMessage());
            }
        } else {
            log.warn("Лидер неизвестен, невозможно перенаправить команду");
        }
    }


    private int getLastLogTerm() {
        if (logEntries.isEmpty()) {
            return 0;
        }
        return logEntries.get(logEntries.size() - 1).getTerm();
    }

    private int getTermAtIndex(int index) {
        if (index >= 0 && index < logEntries.size()) {
            return logEntries.get(index).getTerm();
        }
        return 0;
    }

    public boolean isLogUpToDate(int lastLogIndex, int lastLogTerm) {
        int lastTerm = getLastLogTerm();
        if (lastTerm != lastLogTerm) {
            return lastLogTerm > lastTerm;
        } else {
            return lastLogIndex >= logEntries.size() - 1;
        }
    }

    private void resetElectionTimeout() {
        if (electionScheduler != null && !electionScheduler.isShutdown()) {
            electionScheduler.shutdownNow();
        }
        electionScheduler = Executors.newScheduledThreadPool(1);
        startElectionTimeout();
    }

    /**
     * Инициализация heartbeatScheduler и запуск отправки heartbeat сообщений.
     */
    private void startHeartbeat() {
        if (heartbeatScheduler == null || heartbeatScheduler.isShutdown()) {
            heartbeatScheduler = Executors.newScheduledThreadPool(1);
            // Запускаем отправку heartbeats каждые 100 мс
            heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeats, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    public String get(String key) {
        return stateMachine.get(key);
    }

    public Map<String, String> getAll() {
        return stateMachine.getAll();
    }

    /**
     * Метод для остановки узла (например, при тестировании).
     * Сбрасывает состояние узла и останавливает heartbeat.
     */
    public synchronized void killLeader() {
        if (state == NodeState.LEADER) {
            state = NodeState.FOLLOWER;
            votedFor = null;
            leaderId = null;
            log.info("Узел {} сбрасывается в состояние FOLLOWER", nodeId);
            resetElectionTimeout();
            if (heartbeatScheduler != null) {
                heartbeatScheduler.shutdownNow();
            }
        }
    }
}

