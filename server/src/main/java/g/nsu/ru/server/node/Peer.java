package g.nsu.ru.server.node;


import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@RequiredArgsConstructor
public class Peer {

    final Integer id;

    private final AtomicInteger nextIndex= new AtomicInteger(0);
    private final AtomicInteger matchIndex= new AtomicInteger(-1);
    private final AtomicBoolean voteGranted=new AtomicBoolean(false);

    public Integer getId() {
        return this.id;
    }

    public Integer getNextIndex() {
        return this.nextIndex.get();
    }

    public void setNextIndex(Integer nextIndex) {
        this.nextIndex.set(nextIndex);
    }

    public void decNextIndex() {
        this.nextIndex.decrementAndGet();
    }

    public Integer getMatchIndex() {
        return this.matchIndex.get();
    }

    public Boolean getVoteGranted() {
        return this.voteGranted.get();
    }

    public void setMatchIndex(Integer matchIndex) {
        this.matchIndex.set(matchIndex);
    }

    public void setVoteGranted(Boolean voteGranted) {
        this.voteGranted.set(voteGranted);
    }
}
