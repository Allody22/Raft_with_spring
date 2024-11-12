package g.nsu.ru.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Operation {
    private Long term;
    private OperationType type;
    private Entry entry;
}

