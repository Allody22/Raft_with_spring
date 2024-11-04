package g.nsu.ru.server.model.operations;

import g.nsu.ru.server.node.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;


@Component
@Slf4j
@RequiredArgsConstructor
public class Term {

    private final AtomicLong currentTerm = new AtomicLong(0L);
    private final Attributes attributes;


    public Long getCurrentTerm() {
        return currentTerm.get();
    }

    public void setCurrentTerm(long currentTerm) {
        this.currentTerm.set(currentTerm);
        log.info("Узел #{} поставил терм в {}", attributes.getId(),getCurrentTerm());
    }


    public Long incCurrentTerm() {
        currentTerm.incrementAndGet();
        log.info("Узел #{} повысил терм до: {}",attributes.getId(), getCurrentTerm());
        return getCurrentTerm();
    }
}
