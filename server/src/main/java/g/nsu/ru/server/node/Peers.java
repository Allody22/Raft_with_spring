package g.nsu.ru.server.node;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Peers {


    private final Attributes attributes;

    @Getter
    private Integer quorum;

    @Getter
    private final List<Peer> peers = new ArrayList<>();

    public void add(Integer id) {
        peers.add(new Peer(id));
    }


    public Peer get(Integer id) {
        return peers.stream().
                filter(peer -> peer.getId().equals(id)).
                findFirst().
                orElseThrow(() -> new RuntimeException(String.format("Unsupported peer Id %s", id)));
    }

    public void initQuorum() {
        // Определяем кворум на основе количества узлов
        this.quorum = (peers.size() / 2) + 1;
    }
}
