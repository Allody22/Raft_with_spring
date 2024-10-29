package g.nsu.ru.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "raft")
public class RaftConfig {
    private String nodeId;
    private List<String> peers;
    private int clientPort;
    private Map<String, Integer> nodeClientPorts = new HashMap<>();
}
