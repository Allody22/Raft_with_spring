package g.nsu.ru.server.node;


import g.nsu.ru.server.events.CommittedIndexChangedEvent;
import g.nsu.ru.server.model.State;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

import static g.nsu.ru.server.model.State.FOLLOWER;


@Component
@Slf4j
@RequiredArgsConstructor
@Data
public class Attributes {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Getter
    private Integer id;

    @Getter
    Boolean active = true;

    public void setActive(Boolean active) {
        this.active = active;
        log.info("Peer #{} {}", getId(), active ? "STARTED" : "STOPPED");
    }

    @Getter
    private volatile State state = FOLLOWER;

    @Getter
    private volatile Integer votedFor = null;

    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
        log.debug("Peer #{} set voted for {}", getId(), votedFor);
    }

    private final AtomicInteger commitIndex = new AtomicInteger(-1);
    private final AtomicInteger lastApplied = new AtomicInteger(-1);

    @Getter
    Integer electionTimeout;

    @Getter
    Integer heartBeatTimeout;

    public void setState(State state) {
        if (state != this.state) {
            log.info("Узел #{} обновляет своё состояние до: {}", getId(), state);
        }
        this.state = state;
    }


    public Integer getCommitIndex() {
        return commitIndex.get();
    }

    public void setCommitIndex(Integer commitIndex) {
        this.commitIndex.set(commitIndex);
        log.info("Узел #{} получает новый комит индекс: {}", getId(), this.commitIndex.get());
        applicationEventPublisher.publishEvent(new CommittedIndexChangedEvent(this));
    }

    public Integer getLastApplied() {
        return lastApplied.get();
    }

    public void incLastApplied() {
        log.info("Узел #{} добавленный новый индекс: {}", getId(), this.lastApplied.incrementAndGet());
    }
}
