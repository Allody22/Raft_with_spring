package g.nsu.ru.server.controller;

import g.nsu.ru.server.RaftNode;
import g.nsu.ru.server.model.Command;
import g.nsu.ru.server.model.NodeState;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@AllArgsConstructor
@Slf4j
public class RaftWithClientController {

    private final RaftNode raftNode;

    @PostMapping("/put")
    public ResponseEntity<Void> put(@RequestParam String key, @RequestParam String value) {
        Command command = new Command(Command.CommandType.PUT, key, value);
        raftNode.handleClientCommand(command);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/get/{key}")
    public ResponseEntity<String> get(@PathVariable String key) {
        log.info("try to get value for key {}", key);
        String value = raftNode.get(key);
        return ResponseEntity.ok(value);
    }

    @PostMapping("/delete/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        Command command = new Command(Command.CommandType.DELETE, key, null);
        raftNode.handleClientCommand(command);
        return ResponseEntity.ok().build();
    }

    /**
     * Получение всех значений (GET ALL)
     */
    @GetMapping("/getAll")
    public ResponseEntity<Map<String, String>> getAll() {
        Map<String, String> allData = raftNode.getAll();
        return ResponseEntity.ok(allData);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, String> stateMachineData = raftNode.getAll();
        Map<String, Object> status = Map.of(
                "nodeId", raftNode.getNodeId(),
                "state", raftNode.getState().toString(),
                "currentTerm", raftNode.getCurrentTerm(),
                "leaderId", raftNode.getLeaderId(),
                "currentIndex", raftNode.getLogEntries().size() - 1,
                "stateMachine", stateMachineData
        );
        return ResponseEntity.ok(status);
    }

    @PostMapping("/stepDown")
    public ResponseEntity<Void> stepDown() {
        if (raftNode.getState() == NodeState.LEADER) {
            raftNode.killLeader();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }
}
