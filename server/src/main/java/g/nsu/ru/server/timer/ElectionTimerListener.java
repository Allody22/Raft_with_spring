package g.nsu.ru.server.timer;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ElectionTimerListener implements ApplicationListener<ResetElectionTimerEvent> {

    private final ElectionTimer electionTimer;

    @Override
    public void onApplicationEvent(ResetElectionTimerEvent event) {
        electionTimer.reset();
    }
}
