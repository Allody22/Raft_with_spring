package g.nsu.ru.server.services;


import g.nsu.ru.server.exceptions.NoLeaderInformationException;
import g.nsu.ru.server.exceptions.NotLeaderException;
import g.nsu.ru.server.model.State;
import g.nsu.ru.server.model.operations.*;
import g.nsu.ru.server.node.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static g.nsu.ru.server.model.operations.OperationType.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationsLogService {

    private final OperationsLogInMemory operationsLog;
    private final Attributes attributes;
    private final ReplicationService replicationService;
    private final Term term;


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
        if (!attributes.getState().equals(State.LEADER)) {
            if (attributes.getVotedFor() != null) {
                throw new NotLeaderException(attributes.getVotedFor().toString());
            }else {
                throw new NoLeaderInformationException("Нет инфы");
            }
        }
        log.info("Узел #{} добавил новый лог. {}. key:{} val:{}", attributes.getId(),type,entry.getKey(),entry.getVal());
        Operation operation = new Operation(term.getCurrentTerm(), type, entry);
        operationsLog.append(operation);
        replicationService.appendRequest();
    }

    public void deactivateLeader() {
        if (attributes.getState().equals(State.LEADER)) {
            log.info("Узел #{} больше не лидер, переводим его в фоловера.", attributes.getId());
            attributes.setState(State.FOLLOWER);
            attributes.setVotedFor(null);
        } else {
            if (attributes.getVotedFor() != null) {
                throw new NotLeaderException(attributes.getVotedFor().toString());
            }else {
                throw new NoLeaderInformationException("Нет инфы");
            }
        }
    }

}
