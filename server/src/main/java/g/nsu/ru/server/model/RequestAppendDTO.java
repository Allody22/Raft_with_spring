package g.nsu.ru.server.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import g.nsu.ru.server.model.operations.Operation;
import lombok.Getter;


@Getter
public class RequestAppendDTO {

    private final Long term;
    private final Integer leaderId;
    private final Integer prevLogIndex;
    private final Long prevLogTerm;
    private final Integer leaderCommit;
    private final Operation operation;

    @JsonCreator
    public RequestAppendDTO(
            @JsonProperty("term") Long term,
            @JsonProperty("leaderId") Integer leaderId,
            @JsonProperty("prevLogIndex") Integer prevLogIndex,
            @JsonProperty("prevLogTerm") Long prevLogTerm,
            @JsonProperty("leaderCommit") Integer leaderCommit,
            @JsonProperty("operation") Operation operation) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.leaderCommit = leaderCommit;
        this.operation = operation;
    }
}