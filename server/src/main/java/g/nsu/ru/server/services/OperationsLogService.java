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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationsLogService {

    private final OperationsLogInMemory operationsLog;
    private final Attributes attributes;
    private final ReplicationService replicationService;
    private final Term term;

    private static final long TIMEOUT = 5000; // Timeout in milliseconds

    public boolean compareAndSwap(String key, Object expectedValue, Object newValue) {
        if (!attributes.getState().equals(State.LEADER)) {
            if (attributes.getVotedFor() != null) {
                throw new NotLeaderException(attributes.getVotedFor().toString());
            } else {
                throw new NoLeaderInformationException("No leader information");
            }
        }

        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        Entry entry = new Entry(key, newValue);
        Operation operation = new Operation(term.getCurrentTerm(), OperationType.COMPARE_AND_SWAP, entry, expectedValue);

        operationsLog.append(operation, resultFuture);
        replicationService.appendRequest();

        try {
            return resultFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Error during compareAndSwap operation", e);
            return false;
        }
    }

    public void insert(Entry entry) {
        appendToLog(OperationType.INSERT, entry);
    }

    public void update(String key, Object val) {
        appendToLog(OperationType.UPDATE, new Entry(key, val));
    }

    public void delete(String key) {
        appendToLog(OperationType.DELETE, new Entry(key, null));
    }

    public List<Operation> all() {
        return operationsLog.all();
    }

    private void appendToLog(OperationType type, Entry entry) {
        if (!attributes.getState().equals(State.LEADER)) {
            if (attributes.getVotedFor() != null) {
                throw new NotLeaderException(attributes.getVotedFor().toString());
            } else {
                throw new NoLeaderInformationException("No leader information");
            }
        }
        log.info("Узел #{} добавил новый лог типа: {}. key:{} val:{}", attributes.getId(), type, entry.getKey(), entry.getVal());
        Operation operation = new Operation(term.getCurrentTerm(), type, entry);
        operationsLog.append(operation);
        replicationService.appendRequest();
    }
}
