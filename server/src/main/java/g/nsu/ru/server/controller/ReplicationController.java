package g.nsu.ru.server.controller;

import g.nsu.ru.server.model.AnswerAppendDTO;
import g.nsu.ru.server.model.RequestAppendDTO;
import g.nsu.ru.server.services.ReplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@Slf4j
@RestController
@RequestMapping(value = "/replication",produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
class ReplicationController {

    private final ReplicationService replicationService;

    @PostMapping("/append")
    public AnswerAppendDTO appendRequest(@RequestBody RequestAppendDTO requestAppendDTO,
                                         BindingResult bindingResult) throws BindException {
        if (bindingResult.hasErrors()) {
            log.error("Какой-то пайндинг еррор {}", bindingResult.getAllErrors());
            throw new BindException(bindingResult);
        }
        return replicationService.append(requestAppendDTO);
    }

}
