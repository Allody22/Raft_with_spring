package g.nsu.ru.client;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Operation {

    private Long term;
    private OperationType type;
    private Entry entry;
    private Object expectedValue;

    // Конструктор по умолчанию
    public Operation() {
    }

    // Конструктор для CAS операции
    public Operation(Long term, OperationType type, Entry entry, Object expectedValue) {
        this.term = term;
        this.type = type;
        this.entry = entry;
        this.expectedValue = expectedValue;
    }

    // Конструктор для других операций
    public Operation(Long term, OperationType type, Entry entry) {
        this(term, type, entry, null);
    }
}