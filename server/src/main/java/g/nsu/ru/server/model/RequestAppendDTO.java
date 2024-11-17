package g.nsu.ru.server.model;


import g.nsu.ru.server.model.operations.Operation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Getter
public class RequestAppendDTO {

    private final Long term;
    private final Integer leaderId;

    private final Integer prevLogIndex;

    private final Long prevLogTerm;

    private final Integer leaderCommit;

    private final Operation operation;

}
