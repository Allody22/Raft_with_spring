package g.nsu.ru.server;

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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(NotLeaderException.class)
    public ResponseEntity<ErrorResponse> handleEncryptionException(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Это не лидер, ищите другого"));
    }

//    private final OperationsLogService operationsLogService;
//
//    @ResponseStatus(HttpStatus.BAD_REQUEST)
//    @ExceptionHandler(NotLeaderException.class)
//    public ResponseEntity<Void> handleNotLeaderException(NotLeaderException ex) {
        // Получаем адрес текущего лидера
//        String leaderUrl = operationsLogService.getLeaderUrl(); // Метод для получения URL лидера
//        HttpHeaders headers = new HttpHeaders();
//        headers.setLocation(URI.create(leaderUrl + "/log"));
//        return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT); // 307 статус для редиректа
//    }
}
