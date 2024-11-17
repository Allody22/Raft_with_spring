package g.nsu.ru.server;

import g.nsu.ru.server.exceptions.NoLeaderInformationException;
import g.nsu.ru.server.exceptions.NotLeaderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class RaftExceptionHandler {

    @ResponseBody
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(NotLeaderException.class)
    public ResponseEntity<ErrorResponse> handleNotLeaderException(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage()));
    }


    @ResponseBody
    @ResponseStatus(HttpStatus.ALREADY_REPORTED)
    @ExceptionHandler(NoLeaderInformationException.class)
    public ResponseEntity<ErrorResponse> handleNoLeaderInfoException(Exception ex) {
        return ResponseEntity.status(HttpStatus.ALREADY_REPORTED)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
