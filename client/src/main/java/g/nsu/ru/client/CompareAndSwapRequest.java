package g.nsu.ru.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompareAndSwapRequest {
    private String key;
    private Object expectedValue;
    private Object newValue;

  }
