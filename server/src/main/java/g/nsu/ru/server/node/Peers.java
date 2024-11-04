package g.nsu.ru.server.node;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Component
@RequiredArgsConstructor
public class Peers {


    private Integer quorum;

    private final List<Peer> peers = new ArrayList<>();

    public void add(Integer id) {
        peers.add(new Peer(id));
    }


    public Peer get(Integer id) {
        return peers.stream().
                filter(peer -> peer.getId().equals(id)).
                findFirst().
                orElseThrow(() -> new RuntimeException(String.format("Не поддерживаем такое айди %s", id)));
    }

    public void initQuorum() {
        this.quorum = (int) Math.floor((double) (peers.size() + 1) / 2) + 1;
     }

}
