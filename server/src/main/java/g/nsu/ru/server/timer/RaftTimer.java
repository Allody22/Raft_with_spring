package g.nsu.ru.server.timer;


import g.nsu.ru.server.node.Attributes;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public abstract class RaftTimer {

    protected final Attributes attributes;
    private Timer timer = new Timer();

    abstract protected Integer getTimeout();
    abstract protected String getActionName();
    abstract protected Runnable getAction();
    abstract protected boolean isRun();

    @Getter
    private final AtomicInteger counter = new AtomicInteger(0);

    protected RaftTimer(Attributes attributes) {
        this.attributes = attributes;
    }

    public void reset() {
        counter.set(0); // Сбросить счетчик
    }



    //TODO когда запускает? С точностью в одинаковый интервал
    @PostConstruct
    public void start() {
        stop();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isRun()) {
                    counter.addAndGet(10);
                    if (counter.get() >= getTimeout()) {
                        counter.set(0);
                        getAction().run();
                    }
                } else {
                    log.debug("Время для таймера: {}", getActionName());
                    counter.set(0);
                }
            }
        }, 0, 10); // 10 мс – интервал для проверки состояния
//        log.info("Таймер '{}' запущен со временем {}", getActionName(), getTimeout());
    }

    // Метод для остановки таймера
    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
//            log.info("Таймер '{}' остановлен", getActionName());
        }
    }
}
