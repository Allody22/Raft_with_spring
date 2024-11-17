package g.nsu.ru.server.model.election;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;


@Getter
@Setter
public class AnswerVoteDTO {

    private final Integer id;
    private final Long term;
    private  final  boolean voteGranted;
    private final HttpStatus statusCode;

    @JsonCreator
    public AnswerVoteDTO(@JsonProperty("id") Integer id,
                  @JsonProperty("term") Long term,
                  @JsonProperty("voteGranted") boolean voteGranted) {
        this.id = id;
        this.term = term;
        this.voteGranted = voteGranted;
        this.statusCode = HttpStatus.OK;
    }

    public AnswerVoteDTO(Integer id, HttpStatus statusCode) {
        this.id = id;
        this.statusCode = statusCode;
        term = null;
        voteGranted = false;
    }
}
