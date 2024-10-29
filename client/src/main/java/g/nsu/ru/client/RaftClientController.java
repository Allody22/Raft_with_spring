package g.nsu.ru.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/raft")
@Slf4j
public class RaftClientController {

    private String leaderUrl;
    private final List<String> knownNodes;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final int MAX_REDIRECTS = 5;

    public RaftClientController(@Value("${raft.leader.url}") String leaderUrl,
                                @Value("${raft.nodes}") List<String> knownNodes) {
        this.leaderUrl = leaderUrl;
        this.knownNodes = new ArrayList<>(knownNodes);
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/put")
    public String put(@RequestBody PutRequest putRequest) {
        return sendPutRequest(putRequest.getKey(), putRequest.getValue());
    }

    @GetMapping("/get/{key}")
    public String get(@PathVariable String key) {
        return sendGetRequest(key);
    }

    @GetMapping("/getAll")
    public Map<String, String> getAll() {
        return sendGetAllRequest();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return sendStatusRequest();
    }

    @DeleteMapping("/remove/{key}")
    public String remove(@PathVariable String key) {
        return sendDeleteRequest(key);
    }

    @PostMapping("/kill_leader")
    public String killLeader() {
        return killLeaderRequest();
    }

    private String sendPutRequest(String key, String value) {
        return handleRequest("/put?key=" + key + "&value=" + value, "POST");
    }

    private String sendGetRequest(String key) {
        return handleRequest("/get/" + key, "GET");
    }

    private Map<String, String> sendGetAllRequest() {
        String response = handleRequest("/getAll", "GET");
        try {
            return objectMapper.readValue(response, HashMap.class);
        } catch (IOException e) {
            log.error("Ошибка обработки ответа getAll: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> sendStatusRequest() {
        String response = handleRequest("/status", "GET");
        try {
            return objectMapper.readValue(response, HashMap.class);
        } catch (IOException e) {
            log.error("Ошибка обработки ответа status: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String sendDeleteRequest(String key) {
        return handleRequest("/delete/" + key, "POST");
    }

    private String killLeaderRequest() {
        return handleRequest("/stepDown", "POST");
    }

    private String handleRequest(String endpoint, String method) {
        String currentUrl = leaderUrl;
        int redirectCount = 0;

        while (redirectCount < MAX_REDIRECTS) {
            try {
                String url = currentUrl + endpoint;
                String response;

                // Определяем метод запроса и сохраняем результат
                if ("POST".equals(method)) {
                    response = restTemplate.postForEntity(url, null, String.class).getBody();
                } else {
                    response = restTemplate.getForEntity(url, String.class).getBody();
                }

                leaderUrl = currentUrl;
                return response != null ? response : "Запрос успешно обработан на " + leaderUrl;

            } catch (Exception e) {
                // Обработка редиректа
                log.info("exception {}", e.getMessage());
                String newLeaderUrl = findNextLeader(currentUrl);
                if (newLeaderUrl != null && !newLeaderUrl.equals(currentUrl)) {
                    currentUrl = newLeaderUrl;
                    redirectCount++;
                } else {
                    break;
                }
            }
        }
        log.info("leader is {}", leaderUrl);
        return "Ошибка: Не удалось обработать запрос " + endpoint;
    }


    private String findNextLeader(String currentUrl) {
        int index = knownNodes.indexOf(currentUrl);
        if (index != -1 && index + 1 < knownNodes.size()) {
            return knownNodes.get(index + 1);
        }
        return knownNodes.get(0); // Переход к первому узлу, если достигнут конец списка
    }
}
