package g.nsu.ru.server.model.operations;

import g.nsu.ru.server.node.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class OperationsLogInMemory {

    private static final Integer EMPTY_LOG_LAST_INDEX = -1;

    private final Attributes attributes;

    private final List<Operation> operationsLog = new ArrayList<>();

    private final Map<Integer, CompletableFuture<Boolean>> operationResults = new ConcurrentHashMap<>();

    public synchronized void append(Operation operation, CompletableFuture<Boolean> resultFuture) {
        operationsLog.add(operation);
        int index = operationsLog.size() - 1;
        operationResults.put(index, resultFuture);
    }

    public synchronized void append(Operation operation) {
        operationsLog.add(operation);
    }

    public Operation get(Integer index) {
        return operationsLog.get(index);
    }

    public List<Operation> all() {
        return operationsLog;
    }

    public Integer getLastIndex() {
        return operationsLog.size() - 1;
    }

    public Long getLastTerm() {
        Integer lastIndex = getLastIndex();
        if (lastIndex > EMPTY_LOG_LAST_INDEX) {
            return operationsLog.get(lastIndex).getTerm();
        } else {
            return 0L;
        }
    }

    public synchronized void removeAllFromIndex(int index) {
        log.info("Peer #{} Remove operations from operations. From index {} to {}", attributes.getId(), index, operationsLog.size() - 1);

        int delta = operationsLog.size() - index;
        for (int i = 0; i < delta; i++) {
            operationsLog.remove(index);
        }
    }

    public Long getTerm(Integer index) {
        if (index > EMPTY_LOG_LAST_INDEX) {
            return operationsLog.get(index).getTerm();
        } else {
            return 0L;
        }
    }

    public CompletableFuture<Boolean> getResultFuture(int index) {
        return operationResults.remove(index);
    }
}
