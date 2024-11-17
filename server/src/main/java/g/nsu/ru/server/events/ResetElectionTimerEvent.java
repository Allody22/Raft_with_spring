package g.nsu.ru.server.events;

import org.springframework.context.ApplicationEvent;

public class ResetElectionTimerEvent extends ApplicationEvent {

    public ResetElectionTimerEvent(Object source) {
        super(source);
    }
}