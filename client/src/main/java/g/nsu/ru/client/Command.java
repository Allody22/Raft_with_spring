package g.nsu.ru.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Command implements Serializable {
    public enum CommandType {
        PUT, DELETE
    }

    private CommandType type;
    private String key;
    private String value;
}
