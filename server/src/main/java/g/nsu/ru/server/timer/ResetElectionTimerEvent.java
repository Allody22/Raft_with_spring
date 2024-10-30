package g.nsu.ru.server.timer;

import org.springframework.context.ApplicationEvent;

public class ResetElectionTimerEvent extends ApplicationEvent {

    public ResetElectionTimerEvent(Object source) {
        super(source);
    }
}