package g.nsu.ru.server.model.election;


import lombok.Getter;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Getter
public class RequestVoteDTO {

    private final Long term;

    private final Integer candidateId;

    private final Integer lastLogIndex;

    private final Long lastLogTerm;
}
