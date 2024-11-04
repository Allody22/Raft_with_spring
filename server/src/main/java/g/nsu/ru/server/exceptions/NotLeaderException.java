package g.nsu.ru.server.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Этот сервер не лидер")
public class NotLeaderException extends RuntimeException {

    public NotLeaderException() {
        super("Это не лидер");
    }

    public NotLeaderException(String message) {
        super(message);
    }
}
