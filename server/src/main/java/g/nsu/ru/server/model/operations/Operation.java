package g.nsu.ru.server.model.operations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class Operation {

    private final Long term;
    private final OperationType type;
    private final Entry entry;
    private final Object expectedValue;

    @JsonCreator
    public Operation(
            @JsonProperty("term") Long term,
            @JsonProperty("type") OperationType type,
            @JsonProperty("entry") Entry entry,
            @JsonProperty("expectedValue") Object expectedValue) {
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
