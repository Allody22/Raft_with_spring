package g.nsu.ru.server.model.operations;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class Entry {

    private final String key;
    private final Object val;

}
