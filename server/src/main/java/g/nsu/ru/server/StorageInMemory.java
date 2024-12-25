package g.nsu.ru.server;

import g.nsu.ru.server.model.operations.Entry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class StorageInMemory {

    private final Map<String, Object> storage = new ConcurrentHashMap<>();

    public Object get(String key) {
        return storage.get(key);
    }

    public void insert(String key, Object value) {
        storage.put(key, value);
    }

    public void update(String key, Object value) {
        storage.put(key, value);
    }

    public void delete(String key) {
        storage.remove(key);
    }

    public boolean compareAndSwap(String key, Object expectedValue, Object newValue) {
        if (expectedValue == null) {
            return storage.putIfAbsent(key, newValue) == null;
        } else {
            return storage.replace(key, expectedValue, newValue);
        }
    }

    public List<Entry> all() {
        return storage.entrySet().stream().
                map(entry -> new Entry(entry.getKey(), entry.getValue())).
                collect(Collectors.toList());
    }
}
