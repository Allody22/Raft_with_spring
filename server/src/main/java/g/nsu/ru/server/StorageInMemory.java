package g.nsu.ru.server;

import g.nsu.ru.server.model.operations.Entry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class StorageInMemory {
    private final Map<Long,String> map = new ConcurrentHashMap<>();


    public String get(Long key) {
        return map.get(key);
    }

    public void insert(Long key,
                       String val) {
        map.put(key,val);
    }

    public void update(Long key,
                       String val) {
        map.put(key,val);
    }

    public void delete(Long key) {
        map.remove(key);
    }

    public List<Entry> all() {
        return map.entrySet().stream().
                map(entry -> new Entry(entry.getKey(),entry.getValue())).
                collect(Collectors.toList());
    }
}
