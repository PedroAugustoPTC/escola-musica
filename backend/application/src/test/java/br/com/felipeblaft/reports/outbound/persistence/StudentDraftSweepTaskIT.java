package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

/** Varredura dos cadastros de aluno parados no Telegram (T130, FR-036): apaga os vencidos. */
class StudentDraftSweepTaskIT extends RegistryPersistenceBase {

    @Autowired StudentRegistrationDraftRepository drafts;
    @Autowired StudentDraftSweepTask task;

    private StudentRegistrationDraft draft(UUID linkId, Instant updatedAt, Instant expiresAt) {
        return new StudentRegistrationDraft(
                linkId,
                Kind.CREATE,
                null,
                Set.of(Field.NAME),
                "Aluno em Andamento",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                updatedAt,
                updatedAt,
                expiresAt);
    }

    @Test
    void removesOnlyExpiredDraftsAndKeepsTheLinks() {
        TelegramLink expired = link(teacher("Clara Souza", true), 7_001L, 8_001L);
        TelegramLink active = link(teacher("Bruno Lima", true), 7_002L, 8_002L);
        Instant start = now();
        drafts.save(draft(expired.id(), start, start.plus(Duration.ofMinutes(30))));
        drafts.save(
                draft(
                        active.id(),
                        start.plus(Duration.ofMinutes(20)),
                        start.plus(Duration.ofMinutes(50))));

        clock.advance(Duration.ofMinutes(31));
        task.scan();

        assertThat(drafts.searchByLinkId(expired.id())).isEmpty();
        assertThat(drafts.searchByLinkId(active.id())).isPresent();
        assertThat(links.searchByTelegramUserId(7_001L)).isPresent();
        assertThat(links.searchByTelegramUserId(7_002L)).isPresent();
    }

    @Test
    void scanWithNothingExpiredChangesNothingAndRepeatsSafely() {
        TelegramLink link = link(teacher("Clara Souza", true), 7_001L, 8_001L);
        drafts.save(draft(link.id(), now(), now().plus(Duration.ofMinutes(30))));

        task.scan();
        task.scan();

        assertThat(drafts.searchByLinkId(link.id())).isPresent();
    }

    @Test
    void newActivityPostponesExpiry() {
        TelegramLink link = link(teacher("Clara Souza", true), 7_001L, 8_001L);
        Instant start = now();
        drafts.save(draft(link.id(), start, start.plus(Duration.ofMinutes(30))));
        // nova atividade aos 20 min empurra o vencimento para os 50 min
        drafts.save(
                draft(
                        link.id(),
                        start.plus(Duration.ofMinutes(20)),
                        start.plus(Duration.ofMinutes(50))));

        clock.advance(Duration.ofMinutes(35));
        task.scan();
        assertThat(drafts.searchByLinkId(link.id())).isPresent();

        clock.advance(Duration.ofMinutes(16));
        task.scan();
        assertThat(drafts.searchByLinkId(link.id())).isEmpty();
    }

    @Test
    void scanIsScheduledFrequentlyEnoughForThirtyMinuteDrafts() throws Exception {
        Scheduled scheduled =
                StudentDraftSweepTask.class
                        .getDeclaredMethod("scan")
                        .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isPositive().isLessThanOrEqualTo(10 * 60_000L);
    }
}
