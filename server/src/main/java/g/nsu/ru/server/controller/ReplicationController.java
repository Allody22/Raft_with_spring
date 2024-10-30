package g.nsu.ru.server.controller;

import g.nsu.ru.server.services.AnswerAppendDTO;
import g.nsu.ru.server.services.ReplicationService;
import g.nsu.ru.server.services.RequestAppendDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping(value = "/replication",produces = {MediaType.APPLICATION_JSON_VALUE})
@Api(tags="Replication")
@RequiredArgsConstructor
class ReplicationController {

    private final ReplicationService replicationService;

    @PostMapping("/append")
    @ApiOperation(value = "Append to operations")
    public AnswerAppendDTO appendRequest(@RequestBody RequestAppendDTO requestAppendDTO,
                                         BindingResult bindingResult) throws BindException {
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        return replicationService.append(requestAppendDTO);
    }

}
