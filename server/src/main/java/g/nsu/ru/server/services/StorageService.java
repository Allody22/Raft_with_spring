package g.nsu.ru.server.services;

import g.nsu.ru.server.StorageInMemory;
import g.nsu.ru.server.model.operations.Entry;
import g.nsu.ru.server.model.operations.Operation;
import g.nsu.ru.server.model.operations.OperationsLogInMemory;
import g.nsu.ru.server.node.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageService {

    private final StorageInMemory storage;
    private final Attributes attributes;
    private final OperationsLogInMemory operationsLog;

    public Object get(String id) {
        return storage.get(id);
    }

    public List<Entry> all() {
        return storage.all();
    }

    public void applyCommitted() {
        while (attributes.getLastApplied() < attributes.getCommitIndex()) {
            apply(attributes.getLastApplied() + 1);
        }
    }

    private void apply(Integer index) {
        Operation operation = operationsLog.get(index);
        Entry entry = operation.getEntry();
        log.info("Узел #{} применяет операцию к хранилищу: {} key: {} value: {}", attributes.getId(), operation.getType(), entry.getKey(), entry.getVal());

        boolean result = false;

        switch (operation.getType()) {
            case INSERT:
                storage.insert(entry.getKey(), entry.getVal());
                result = true;
                break;
            case UPDATE:
                storage.update(entry.getKey(), entry.getVal());
                result = true;
                break;
            case DELETE:
                storage.delete(entry.getKey());
                result = true;
                break;
            case COMPARE_AND_SWAP:
                result = storage.compareAndSwap(entry.getKey(), operation.getExpectedValue(), entry.getVal());
                break;
            default:
                throw new RuntimeException("Unsupported operation");
        }

        CompletableFuture<Boolean> resultFuture = operationsLog.getResultFuture(index);
        if (resultFuture != null) {
            resultFuture.complete(result);
        }

        attributes.incLastApplied();
    }
}
