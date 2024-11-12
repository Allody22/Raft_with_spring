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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
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

        return ResponseEntity.status(HttpStatus.OK).body(new StringResponse(jsonResponse));
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
        try {
            return objectMapper.readValue(response, new TypeReference<List<Entry>>() {
            });
        } catch (IOException e) {
            log.error("Ошибка обработки ответа getAll: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Operation> sendGetAllLogsRequest() {
        String response = handleRequest("/raft/logs/get/all", "GET");
        try {
            if (!response.startsWith("[")) {
                log.error("Ошибка обработки ответа get all logs: невалидный JSON: {}", response);
                return new ArrayList<>();
            }
            return objectMapper.readValue(response, new TypeReference<List<Operation>>() {
            });
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

    private String handleRequestKillLeader() {
        String currentUrl = leaderUrl;

        for (int i = 0; i < 2; i++) {
            try {
                String url = currentUrl + "/raft/kill-leader";
                String response;

                response = restTemplate.postForEntity(url, null, String.class).getBody();

                leaderUrl = currentUrl;
                return response != null ? response : "Запрос успешно обработан на " + leaderUrl;

            } catch (Exception e) {
                log.info("exception {}", e.getMessage());
                var leaderId = e.getMessage();
                currentUrl = "800" + leaderId;
                leaderUrl = currentUrl;
            }
        }
        log.info("leader is {}", leaderUrl);
        return "Ошибка: Не удалось обработать запрос " + "/raft/kill-leader";
    }

    private String handleRequest(String endpoint, String method, Entry entry) {
        String currentUrl = leaderUrl;

        for (int i = 0; i < knownNodes.size(); i++) {
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

                if (!Objects.equals(leaderUrl, currentUrl)) {
                    leaderUrl = currentUrl;
                    log.info("Лидер теперь это {}", leaderUrl);
                }
                return response != null ? response : "Запрос успешно обработан на " + leaderUrl;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(e.getResponseBodyAsString());
                        String leaderId = jsonNode.get("message").asText();
                        log.info("Получен новый ID лидера: {}", leaderId);
                        currentUrl = "http://localhost:800" + leaderId;
                        leaderUrl = currentUrl;
                    } catch (IOException jsonParseException) {
                        log.error("Ошибка парсинга JSON из NotLeaderException: {}", jsonParseException.getMessage());
                    }
                } else if (e.getStatusCode() == HttpStatus.ALREADY_REPORTED) {
                    log.info("У данного узла нет информации о лидере");
                }
            } catch (ResourceAccessException e) {
                log.info("Узел {} недоступен: {}", currentUrl, e.getMessage());
                if (i < knownNodes.size() - 1) {
                    currentUrl = knownNodes.get(i + 1);
                } else {
                    log.error("Все известные узлы недоступны.");
                }
            } catch (Exception e) {
                log.error("Неизвестная ошибка при запросе к {}: {}", currentUrl, e.getMessage());
                break;
            }
        }

        log.info("Лидер с ошибкой: {}", leaderUrl);
        return "Ошибка: Не удалось обработать запрос " + endpoint;
    }

    private String handleRequest(String endpoint, String method) {
        String currentUrl = leaderUrl;

        for (int i = 0; i < knownNodes.size(); i++) {
            try {
                String url = currentUrl + endpoint;
                String response;

                // Выполняем запрос в зависимости от метода
                if ("POST".equals(method)) {
                    response = restTemplate.postForEntity(url, null, String.class).getBody();
                } else {
                    response = restTemplate.getForEntity(url, String.class).getBody();
                }

                if (!Objects.equals(leaderUrl, currentUrl)) {
                    leaderUrl = currentUrl;
                    log.info("Лидер теперь это {}", leaderUrl);
                }
                return response != null ? response : "Запрос успешно обработан на " + leaderUrl;

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(e.getResponseBodyAsString());
                        String leaderId = jsonNode.get("message").asText();
                        log.info("Получен новый ID лидера: {}", leaderId);
                        currentUrl = "http://localhost:800" + leaderId;
                        leaderUrl = currentUrl;
                    } catch (IOException jsonParseException) {
                        log.error("Ошибка парсинга JSON из NotLeaderException: {}", jsonParseException.getMessage());
                    }
                } else {
                    log.error("Неожиданный статус ошибки: {} при запросе к {}", e.getStatusCode(), currentUrl);
                }
            } catch (ResourceAccessException e) {
                log.info("Узел {} недоступен: {}", currentUrl, e.getMessage());
                if (i < knownNodes.size() - 1) {
                    currentUrl = knownNodes.get(i + 1);
                } else {
                    log.error("Все известные узлы недоступны.");
                }
            } catch (Exception e) {
                log.error("Неизвестная ошибка при запросе к {}: {}", currentUrl, e.getMessage());
                break;
            }
        }

        log.info("Текущий лидер: {}", leaderUrl);
        return "Ошибка: Не удалось обработать запрос " + endpoint;
    }


}
