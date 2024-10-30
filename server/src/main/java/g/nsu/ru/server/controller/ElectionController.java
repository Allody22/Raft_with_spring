package g.nsu.ru.server.controller;

import g.nsu.ru.server.model.election.AnswerVoteDTO;
import g.nsu.ru.server.model.election.RequestVoteDTO;
import g.nsu.ru.server.services.ElectionService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping(value = "/election",produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Slf4j
class ElectionController {

    private final ElectionService electionService;

    @PostMapping("/vote")
    @ApiOperation(value = "Request vote")
    public AnswerVoteDTO voteDTO(@RequestBody RequestVoteDTO requestVoteDTO,
                                 BindingResult bindingResult) throws BindException {
        log.info("START ELECTION");
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        return electionService.vote(requestVoteDTO);
    }

}
