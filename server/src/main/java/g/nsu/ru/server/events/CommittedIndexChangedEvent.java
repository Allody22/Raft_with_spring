package g.nsu.ru.server.events;

import org.springframework.context.ApplicationEvent;

public class CommittedIndexChangedEvent extends ApplicationEvent {

    public CommittedIndexChangedEvent(Object source) {
        super(source);
    }
}