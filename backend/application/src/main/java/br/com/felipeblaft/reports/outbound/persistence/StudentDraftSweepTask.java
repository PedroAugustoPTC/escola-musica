package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Varredura dos cadastros de aluno parados no Telegram. Stub da Fundação (feature 005): o fluxo W-G
 * implementa {@link #scan()} e o agenda com {@code @Scheduled}.
 */
@Component
@Profile("!seed")
class StudentDraftSweepTask {

    private final StudentRegistrationDraftRepository drafts;
    private final ClockPort clock;

    StudentDraftSweepTask(StudentRegistrationDraftRepository drafts, ClockPort clock) {
        this.drafts = drafts;
        this.clock = clock;
    }

    void scan() {
        throw PendingImplementation.of("W-G");
    }
}
