package g.nsu.ru.server.timer;


import g.nsu.ru.server.node.Attributes;
import g.nsu.ru.server.services.ElectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static g.nsu.ru.server.model.State.LEADER;


@Slf4j
@Service
public class ElectionTimer extends RaftTimer {

    private final ElectionService electionService;

    protected ElectionTimer(Attributes attributes, ElectionService electionService) {
        super(attributes);
        this.electionService = electionService;
    }

    @Override
    protected Integer getTimeout() {
        return attributes.getElectionTimeout();
    }

    @Override
    protected String getActionName() {
        return "vote";
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
