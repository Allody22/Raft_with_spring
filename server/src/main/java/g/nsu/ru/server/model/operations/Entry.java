package g.nsu.ru.server.model.operations;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;


@Getter
public class Entry {

    private final String key;
    private final Object val;

    @JsonCreator
    public Entry(
            @JsonProperty("key") String key,
            @JsonProperty("val") Object val) {
        this.key = key;
        this.val = val;
    }
}
