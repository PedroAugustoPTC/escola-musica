package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.privacy.ConsentCollectionMethod;
import br.com.felipeblaft.reports.domain.privacy.ConsentRelationship;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryConsentRepositoriesTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");
    private final InMemoryConsentRepository consents = new InMemoryConsentRepository();
    private final InMemoryConsentTermRepository terms = new InMemoryConsentTermRepository();

    private static GuardianConsent consent(UUID studentId, Instant recordedAt) {
        return new GuardianConsent(
                UUID.randomUUID(),
                studentId,
                "Maria Guardian",
                ConsentRelationship.MOTHER,
                LocalDate.of(2026, 9, 1),
                UUID.randomUUID(),
                1,
                ConsentCollectionMethod.DIGITAL,
                UUID.randomUUID(),
                Role.ADMINISTRATOR,
                ActionOrigin.WEB,
                recordedAt,
                null,
                null,
                null);
    }

    @Test
    void historyComesNewestFirstAndSavingTheSameIdReplacesTheRecord() {
        UUID student = UUID.randomUUID();
        GuardianConsent older = consent(student, NOW);
        GuardianConsent newer = consent(student, NOW.plusSeconds(60));
        consents.save(older);
        consents.save(newer);

        assertThat(consents.findByStudent(student)).containsExactly(newer, older);
        GuardianConsent revoked =
                older.revoke(
                        LocalDate.of(2026, 9, 5),
                        "MOTHER",
                        UUID.randomUUID(),
                        LocalDate.of(2026, 9, 21));
        consents.save(revoked);

        assertThat(consents.size()).isEqualTo(2);
        assertThat(consents.findById(older.id())).contains(revoked);
        assertThat(consents.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void studentsWithoutRecordsAreAbsentFromTheBatchResult() {
        UUID withHistory = UUID.randomUUID();
        UUID without = UUID.randomUUID();
        consents.save(consent(withHistory, NOW));

        assertThat(consents.findByStudents(List.of(withHistory, without)).keySet())
                .containsExactly(withHistory);
    }

    @Test
    void termsComeFromTheNewestToTheOldest() {
        UUID admin = UUID.randomUUID();
        terms.save(
                new ConsentTermVersion(
                        UUID.randomUUID(), 1, LocalDate.of(2026, 1, 1), "A", false, admin, NOW));
        terms.save(
                new ConsentTermVersion(
                        UUID.randomUUID(), 2, LocalDate.of(2026, 2, 1), "B", true, admin, NOW));

        assertThat(terms.listAll()).extracting(ConsentTermVersion::number).containsExactly(2, 1);
    }
}
