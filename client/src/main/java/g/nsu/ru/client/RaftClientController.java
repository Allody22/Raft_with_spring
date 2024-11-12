package g.nsu.ru.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<StringResponse> put(@RequestBody PutRequest putRequest) {
        String jsonResponse = sendPutRequest(putRequest.getKey(), putRequest.getVal());

        // Распарсить JSON и извлечь значение "result"
        ObjectMapper objectMapper = new ObjectMapper();
        String result;
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            result = jsonNode.get("result").asText();
        } catch (Exception e) {
            result = "Ошибка обработки ответа";
        }

        return ResponseEntity.status(HttpStatus.OK).body(new StringResponse(result));
    }
    @GetMapping("/get/{key}")
    public ResponseEntity<StringResponse> get(@PathVariable String key) {
        String jsonResponse = sendGetRequest(key);
        ObjectMapper objectMapper = new ObjectMapper();
        String result;
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            result = jsonNode.get("val").asText();
        } catch (Exception e) {
            result = "Ошибка обработки ответа";
        }

        return ResponseEntity.ok().body(new StringResponse(result));
    }

    @GetMapping("/getall")
    public List<Entry> getAll() {
        return sendGetAllRequest();
    }

    @GetMapping("/logs/get/all")
    public ResponseEntity<List<Operation>> getAllLogs() {
        return ResponseEntity.ok().body(sendGetAllLogsRequest());
    }

//    @GetMapping("/status")
//    public Map<String, Object> status() {
//        return sendStatusRequest();
//    }

    @DeleteMapping("/remove/{key}")
    public ResponseEntity<StringResponse> remove(@PathVariable String key) {
        String jsonResponse = sendDeleteRequest(key);
        ObjectMapper objectMapper = new ObjectMapper();
        String result;
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            result = jsonNode.get("result").asText();
        } catch (Exception e) {
            result = "Ошибка обработки ответа";
        }

        return ResponseEntity.ok().body(new StringResponse(result));
    }

//    @PostMapping("/kill-leader")
//    public String killLeader() {
//        return killLeaderRequest();
//    }

//    private String sendPutRequest(String key, String value) {
//        return handleRequest("/put?key=" + key + "&value=" + value, "POST");
//    }

    private String sendGetRequest(String key) {

        return handleRequest("/raft/get/" + key, "GET");
    }


    private List<Entry> sendGetAllRequest() {
        String response = handleRequest("/raft/getall", "GET");
        log.info("response: {}", response);
        try {
            return objectMapper.readValue(response, new TypeReference<List<Entry>>() {
            });
        } catch (IOException e) {
            log.error("Ошибка обработки ответа getAll: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Operation> sendGetAllLogsRequest() {
        String response = handleRequest("/raft/status", "GET");
        try {
            return objectMapper.readValue(response, new TypeReference<List<Operation>>() {});
        } catch (IOException e) {
            log.error("Ошибка обработки ответа get all logs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> sendStatusRequest() {
        String response = handleRequest("/raft/status", "GET");
        try {
            return objectMapper.readValue(response, HashMap.class);
        } catch (IOException e) {
            log.error("Ошибка обработки ответа status: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String sendDeleteRequest(String key) {
        return handleRequest("/raft/delete/" + key, "POST");
    }

    private String killLeaderRequest() {
        return handleRequestKillLeader();
    }

    private String sendPutRequest(Long key, String value) {
        Entry entry = new Entry(key, value);
        return handleRequest("/raft/put", "POST", entry);
    }

    private String handleRequest(String endpoint, String method, Entry entry) {
        String currentUrl = leaderUrl;
        int redirectCount = 0;

        while (redirectCount < MAX_REDIRECTS) {
            try {
                String url = currentUrl + endpoint;
                String response;

                if ("PUT".equals(method)) {
                    HttpEntity<Entry> requestEntity = new HttpEntity<>(entry);
                    response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class).getBody();
                } else if ("POST".equals(method)) {
                    response = restTemplate.postForEntity(url, entry, String.class).getBody();
                } else {
                    response = restTemplate.getForEntity(url, String.class).getBody();
                }

                leaderUrl = currentUrl;
                return response != null ? response : "Запрос успешно обработан на " + leaderUrl;

            } catch (Exception e) {
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

    private String handleRequestKillLeader() {
        String currentUrl = leaderUrl;
        int redirectCount = 0;

        while (redirectCount < MAX_REDIRECTS) {
            try {
                String url = currentUrl + "/raft/kill-leader";
                String response;

                response = restTemplate.postForEntity(url, null, String.class).getBody();

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
        return "Ошибка: Не удалось обработать запрос " + "/raft/kill-leader";
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
