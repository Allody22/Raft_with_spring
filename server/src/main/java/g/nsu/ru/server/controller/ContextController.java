package g.nsu.ru.server.controller;

import g.nsu.ru.server.node.RaftNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(value = "/context",produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Slf4j
class ContextController {

    private final RaftNode raftNode;

    @GetMapping
    public RaftNode getCurrentPeerState()  {
      return raftNode;
    }

    @PostMapping("/stop")
    public void stop()  {
        raftNode.setActive(false);
    }

    @PostMapping("/start")
    public void start()  {
        raftNode.setActive(true);
    }
}
