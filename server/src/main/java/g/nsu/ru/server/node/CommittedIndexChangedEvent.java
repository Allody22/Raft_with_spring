package g.nsu.ru.server.node;

import org.springframework.context.ApplicationEvent;

public class CommittedIndexChangedEvent extends ApplicationEvent {

    public CommittedIndexChangedEvent(Object source) {
        super(source);
    }
}
