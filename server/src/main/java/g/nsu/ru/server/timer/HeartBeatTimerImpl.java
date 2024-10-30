package g.nsu.ru.server.timer;


import g.nsu.ru.server.node.Attributes;
import g.nsu.ru.server.services.ReplicationService;
import org.springframework.stereotype.Service;

import static g.nsu.ru.server.model.State.LEADER;


@Service
class HeartBeatTimerImpl extends RaftTimer {


    private final ReplicationService replicationService;

    protected HeartBeatTimerImpl(Attributes attributes, ReplicationService replicationService) {
        super(attributes);
        this.replicationService = replicationService;
    }

    protected Integer getTimeout() {
        return attributes.getHeartBeatTimeout();
    }

    protected String getActionName() {
        return "heart beat";
    }

    protected Runnable getAction() {
        return replicationService::appendRequest;
    }

    protected boolean isRun() {
            return attributes.getActive() && attributes.getState().equals(LEADER);
    }


}
