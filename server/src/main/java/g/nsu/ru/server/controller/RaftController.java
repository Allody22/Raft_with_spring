package g.nsu.ru.server.controller;

import g.nsu.ru.server.RaftNode;
import g.nsu.ru.server.model.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@Slf4j
public class RaftController {

    private final RaftNode raftNode;

    @PostMapping("/appendEntries")
    public ResponseEntity<AppendEntriesResponse> appendEntries(@RequestBody AppendEntriesRequest request) {
        boolean success = raftNode.handleAppendEntries(request);
        AppendEntriesResponse response = new AppendEntriesResponse(raftNode.getCurrentTerm(), success);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requestVote")
    public ResponseEntity<RequestVoteResponse> requestVote(@RequestBody RequestVoteRequest request) {
        boolean voteGranted = raftNode.handleRequestVote(request);
        RequestVoteResponse response = new RequestVoteResponse(raftNode.getCurrentTerm(), voteGranted);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/clientCommand")
    public ResponseEntity<Void> clientCommand(@RequestBody Command command) {
        raftNode.handleClientCommand(command);
        return ResponseEntity.ok().build();
    }
}
