package g.nsu.ru.server;

import g.nsu.ru.server.model.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Service
@Slf4j
@AllArgsConstructor
public class RaftCommunicationService {

    private final RestTemplate restTemplate;

    public AppendEntriesResponse sendAppendEntries(String url, AppendEntriesRequest request) {
        try {
            return restTemplate.postForObject(url + "/appendEntries", request, AppendEntriesResponse.class);
        } catch (Exception e) {
//            log.error("ОШИБКА В SEND APPEND ENTRIES {} а именно {}", url, e.getMessage());
            return null;
        }
    }

    public RequestVoteResponse sendRequestVote(String url, RequestVoteRequest request) {
        try {
            return restTemplate.postForObject(url + "/requestVote", request, RequestVoteResponse.class);
        } catch (Exception e) {
//            log.error("ОШИБКА В REQUEST VOTE к узлу {}: {}", url, e.getMessage());
            return null;
        }
    }


    public void sendClientCommand(String url, Command command) throws IOException {
        try {
            restTemplate.postForObject(url + "/clientCommand", command, Void.class);
        } catch (Exception e) {
            throw new IOException("Ошибка при отправке команды лидеру: " + e.getMessage(), e);
        }
    }
}
