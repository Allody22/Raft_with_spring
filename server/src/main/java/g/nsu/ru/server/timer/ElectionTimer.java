package g.nsu.ru.server.timer;


import g.nsu.ru.server.node.Attributes;
import g.nsu.ru.server.services.ElectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

import static g.nsu.ru.server.model.State.LEADER;


@Slf4j
@Service
public class ElectionTimer extends RaftTimer {

    private final ElectionService electionService;
    private Integer electionTimeout; // Время выбора, обновляется при новых выборах

    protected ElectionTimer(Attributes attributes, @Lazy ElectionService electionService) {
        super(attributes);
        this.electionService = electionService;
    }

    @Override
    protected Integer getTimeout() {
        if (electionTimeout == null) {
            resetElectionTimeout();
        }
        return electionTimeout;
    }

    public void resetElectionTimeout() {
        stop();
        electionTimeout = ThreadLocalRandom.current().nextInt(1500, attributes.getElectionTimeout());
        reset();
        start();
        log.info("Новое время таймера выборов: {}", electionTimeout);
    }


    @Override
    protected String getActionName() {
        return "election";
    }


    @Override
    protected Runnable getAction() {
        return electionService::processElection;
    }

    @Override
    protected boolean isRun() {
        return attributes.getActive() && !attributes.getState().equals(LEADER);
    }

}
