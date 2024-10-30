package g.nsu.ru.server.services;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public class AnswerAppendDTO {

    private  final Integer id;

    private  final  Long term;

    private  final Boolean success;

    private  final Integer matchIndex;

    private final HttpStatus statusCode;

    @JsonCreator
    AnswerAppendDTO(
            @JsonProperty("id") Integer id,
            @JsonProperty("term") Long term,
            @JsonProperty("success")  Boolean success,
            @JsonProperty("matchIndex") Integer matchIndex)
    {
        this.id = id;
        this.term = term;
        this.success = success;
        this.matchIndex = matchIndex;
        this.statusCode = HttpStatus.OK;
    }


    AnswerAppendDTO(Integer id, HttpStatus statusCode) {
        this.id = id;
        this.statusCode = statusCode;
        this.term = null;
        this.success = false;
        matchIndex = null;
    }
}
