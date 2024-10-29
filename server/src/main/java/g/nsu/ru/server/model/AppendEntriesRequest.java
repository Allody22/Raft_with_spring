package g.nsu.ru.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppendEntriesRequest {
    private Integer term;
    private String leaderId;
    private Integer prevLogIndex;
    private Integer prevLogTerm;
    private List<LogEntry> entries;
    private Integer leaderCommit;
}

