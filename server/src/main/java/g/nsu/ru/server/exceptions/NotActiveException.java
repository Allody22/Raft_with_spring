package g.nsu.ru.server.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.SERVICE_UNAVAILABLE, reason = "Что-то не так работает")
public class NotActiveException extends RuntimeException {

    public NotActiveException() {
        super("Эта нода сейчас не активна ");
    }
}
