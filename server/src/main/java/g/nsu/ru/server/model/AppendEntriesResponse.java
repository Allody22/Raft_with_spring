package g.nsu.ru.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppendEntriesResponse {
    private Integer term;
    private Boolean success;
}

