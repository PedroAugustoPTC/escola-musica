package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredura dos cadastros de aluno parados no Telegram (vencem em 30 minutos sem atividade). Roda a
 * cada 5 minutos; a próxima atualização do vínculo também descarta o vencido, com aviso.
 */
@Component
@Profile("!seed")
class StudentDraftSweepTask {

    private static final Logger LOG = LoggerFactory.getLogger(StudentDraftSweepTask.class);

    private final StudentRegistrationDraftRepository drafts;
    private final ClockPort clock;

    StudentDraftSweepTask(StudentRegistrationDraftRepository drafts, ClockPort clock) {
        this.drafts = drafts;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    void scan() {
        int removed = drafts.removeExpired(clock.now());
        if (removed > 0) {
            LOG.info("Varredura de cadastros de aluno parados: {} removido(s).", removed);
        }
    }
}
