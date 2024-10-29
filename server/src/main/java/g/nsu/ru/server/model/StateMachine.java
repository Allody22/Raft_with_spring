package g.nsu.ru.server.model;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class StateMachine {
    private Map<String, String> store = new ConcurrentHashMap<>();

    public void apply(Command command) {
        if (command.getType() == Command.CommandType.PUT) {
            store.put(command.getKey(), command.getValue());
        } else if (command.getType() == Command.CommandType.DELETE) {
            store.remove(command.getKey());
        }
    }


    public String get(String key) {
        String value = store.get(key);
        return Objects.requireNonNullElse(value, "Ошибка: ключа " + key + " не существует");
    }

    public Map<String, String> getAll() {
        return new HashMap<>(store);
    }

}



