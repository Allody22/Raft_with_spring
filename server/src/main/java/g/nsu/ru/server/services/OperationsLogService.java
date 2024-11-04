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

    public String getLeaderUrl() {
        Integer leaderId = attributes.getLeaderId();
        if (leaderId != null) {
            return "http://localhost:800" + leaderId;
        }
        throw new IllegalStateException("Лидер не определён");
    }

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


    public List<Operation> allValues() {
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

    public void deactivateLeader() {
        if (attributes.getState().equals(State.LEADER)) {
            log.info("Узел #{} больше не лидер, переводим его в фоловера.", attributes.getId());
            attributes.setState(State.FOLLOWER);
            attributes.setLeaderId(null);
            attributes.setVotedFor(null);
        } else {
            throw new NotLeaderException("Узел не является лидером и не может быть деактивирован таким образом.");
        }
    }

}
