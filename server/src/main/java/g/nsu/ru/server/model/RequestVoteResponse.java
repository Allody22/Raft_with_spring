package g.nsu.ru.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestVoteResponse implements Serializable {
    private Integer term;
    private Boolean voteGranted;
}

