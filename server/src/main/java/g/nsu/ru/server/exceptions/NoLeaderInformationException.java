package g.nsu.ru.server.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.ALREADY_REPORTED, reason = "Этот сервер не лидер")
public class NoLeaderInformationException extends RuntimeException {

    public NoLeaderInformationException() {
        super("Это не лидер");
    }

    public NoLeaderInformationException(String message) {
        super(message);
    }
}
