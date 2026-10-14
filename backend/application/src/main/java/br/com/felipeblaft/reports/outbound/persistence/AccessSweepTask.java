package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.inbound.telegram.TelegramUpdateRecords;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Varredura diária: sessões vencidas e updates do Telegram com mais de 7 dias. */
@Component
@Profile("!seed")
class AccessSweepTask {

    private static final Logger LOG = LoggerFactory.getLogger(AccessSweepTask.class);
    static final Duration RETENTION_UPDATES = Duration.ofDays(7);

    private final WebSessionRepository sessions;
    private final TelegramUpdateRecords updates;
    private final ClockPort clock;

    AccessSweepTask(WebSessionRepository sessions, TelegramUpdateRecords updates, ClockPort clock) {
        this.sessions = sessions;
        this.updates = updates;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "America/Sao_Paulo")
    void scan() {
        Instant now = clock.now();
        int removedSessions = sessions.removeExpired(now);
        int updatesRemoved = updates.removeOld(now.minus(RETENTION_UPDATES));
        LOG.info("access-sweep sessions={} updates={}", removedSessions, updatesRemoved);
    }
}
