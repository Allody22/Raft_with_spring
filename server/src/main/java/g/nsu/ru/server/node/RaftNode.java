package g.nsu.ru.server.node;


import g.nsu.ru.server.model.RaftConfig;
import g.nsu.ru.server.model.State;
import g.nsu.ru.server.model.operations.OperationsLogInMemory;
import g.nsu.ru.server.model.operations.Term;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static g.nsu.ru.server.model.State.FOLLOWER;

@Component
@Slf4j
@Data
@RequiredArgsConstructor
public class RaftNode {

    private final Peers peers;
    private final Term term;
    private final Attributes attributes;
    private final OperationsLogInMemory operationsLog;

    @Autowired
    public RaftNode(Peers peers, Term term, Attributes attributes, OperationsLogInMemory operationsLog, RaftConfig raftConfig) {
        this.peers = peers;
        this.term = term;
        this.attributes = attributes;
        this.operationsLog = operationsLog;

        log.info("Контекст: {}", raftConfig);
        attributes.setId(raftConfig.getNodeId());
        attributes.setElectionTimeout(raftConfig.getElectionTimeout());
        attributes.setHeartBeatTimeout(raftConfig.getHeartBeatTimeout());
        setActive(true);
        // Добавляем peers и настраиваем кворум
        for (Integer peerId : raftConfig.getPeers()) {
            peers.add(peerId);
        }
        peers.initQuorum();
    }

    public Integer getId() {
        return attributes.getId();
    }

    public void setActive(Boolean active) {
        attributes.setActive(active);
    }

    public Boolean getActive() {
        return attributes.getActive();
    }

    public void cancelIfNotActive() {
        attributes.cancelIfNotActive();
    }

    public State getState() {
        return attributes.getState();
    }

    public void setState(State state) {
        attributes.setState(state);
    }

    public void setLeaderId(Integer leaderId) {
        attributes.setLeaderId(leaderId);
    }

    public Integer getLeaderId() {
        return attributes.getLeaderId();
    }

    public Long getCurrentTerm() {
        return term.getCurrentTerm();
    }

    public Integer getCommitIndex() {
        return attributes.getCommitIndex();
    }

    public void setCommitIndex(Integer commitIndex) {
        attributes.setCommitIndex(commitIndex);
    }

    public Integer getLastApplied() {
        return attributes.getLastApplied();
    }

    public Integer getElectionTimeout() {
        return attributes.getElectionTimeout();
    }

    public Integer getHeartBeatTimeout() {
        return attributes.getHeartBeatTimeout();
    }

    public List<Peer> getPeers() {
        return peers.getPeers();
    }

    public Peer getPeer(Integer id)  {
        return  peers.get(id);
    }

    public Long incCurrentTerm() {
       return term.incCurrentTerm();
    }

    public void setCurrentTerm(Long term) {
        this.term.setCurrentTerm(term);
    }

    public Integer getLastIndex() {
        return operationsLog.getLastIndex();
    }

    public Integer getQuorum() {
        return peers.getQuorum();
    }

    public Integer getVotedFor() {
       return attributes.getVotedFor();
    }

    public void setVotedFor(Integer votedFor) {
        attributes.setVotedFor(votedFor);
    }

    public void setTermGreaterThenCurrent(Long term) {
        log.info("Узел #{} получил терм {} больше чем текущий. текущий терм {}", getId(),term,getCurrentTerm());
        setState(FOLLOWER);
        setCurrentTerm(term);
        setVotedFor(null);
    }
}
