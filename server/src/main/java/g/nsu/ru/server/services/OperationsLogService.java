package g.nsu.ru.server.services;


import g.nsu.ru.server.events.OperationsLogAppendedEvent;
import g.nsu.ru.server.exceptions.NotLeaderException;
import g.nsu.ru.server.model.State;
import g.nsu.ru.server.model.operations.*;
import g.nsu.ru.server.node.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

import static g.nsu.ru.server.model.operations.OperationType.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationsLogService {

    private final OperationsLogInMemory operationsLog;
    private final Attributes attributes;
    private final Term term;
    private final ApplicationEventPublisher applicationEventPublisher;


    public void insert(Entry entry) {
        appendToLog(INSERT, entry);
    }

    public void update(Long key, String val) {
        appendToLog(UPDATE, new Entry(key, val));
    }

    public void delete(Long key) {
        appendToLog(DELETE, new Entry(key, null));
    }

    public List<Operation> all() {
        return operationsLog.all();
    }

    private void appendToLog(OperationType type, Entry entry) {
        log.info("Узел #{} добавил новый лог. {}. key:{} val:{}", attributes.getId(),type,entry.getKey(),entry.getVal());
        attributes.cancelIfNotActive();
        if (!attributes.getState().equals(State.LEADER)) {
            throw new NotLeaderException();
        }
        Operation operation = new Operation(term.getCurrentTerm(), type, entry);
        operationsLog.append(operation);
        applicationEventPublisher.publishEvent(new OperationsLogAppendedEvent(this));
    }
}