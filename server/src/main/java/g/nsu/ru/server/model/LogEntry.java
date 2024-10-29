package g.nsu.ru.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry implements Serializable {
    private Integer term;
    private Command command;

}

