package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryStudentRegistrationDraftRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration VALIDITY = Duration.ofMinutes(30);

    private final InMemoryStudentRegistrationDraftRepository drafts =
            new InMemoryStudentRegistrationDraftRepository();

    private StudentRegistrationDraft draft(UUID linkId, Instant now) {
        return StudentRegistrationDraft.start(linkId, Kind.CREATE, null, now, VALIDITY);
    }

    @Test
    void savesOneDraftPerLinkAndTheNewOneReplacesThePrevious() {
        UUID link = UUID.randomUUID();
        drafts.save(draft(link, NOW));
        StudentRegistrationDraft newer = draft(link, NOW).withName("Ana", NOW, VALIDITY);
        drafts.save(newer);

        assertThat(drafts.searchByLinkId(link)).contains(newer);
        assertThat(drafts.all()).hasSize(1);
        assertThat(drafts.saves()).isEqualTo(2);
        assertThat(drafts.searchByLinkId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void removalCountsOnlyWhatWasRemoved() {
        UUID link = UUID.randomUUID();
        drafts.save(draft(link, NOW));
        drafts.removeByLinkId(UUID.randomUUID());
        assertThat(drafts.removals()).isZero();
        drafts.removeByLinkId(link);
        assertThat(drafts.removals()).isEqualTo(1);
        assertThat(drafts.all()).isEmpty();
    }

    @Test
    void sweepRemovesOnlyTheExpired() {
        UUID old = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        drafts.save(draft(old, NOW.minus(Duration.ofHours(1))));
        drafts.save(draft(fresh, NOW));

        assertThat(drafts.removeExpired(NOW)).isEqualTo(1);
        assertThat(drafts.all())
                .extracting(StudentRegistrationDraft::linkId)
                .containsExactly(fresh);
        assertThat(drafts.removals()).isEqualTo(1);
    }

    @Test
    void endOfTheLinkRemovesTheDraftByCascade() {
        InMemoryTelegramLinkRepository links = new InMemoryTelegramLinkRepository();
        links.cascadeRemovalTo(drafts::removeByLinkId);
        TelegramLink link = TelegramLink.create(UUID.randomUUID(), 1L, 2L, NOW);
        links.save(link);
        drafts.save(draft(link.id(), NOW));

        links.remove(link.id());

        assertThat(drafts.searchByLinkId(link.id())).isEmpty();
    }

    @Test
    void decryptionFakeUndoesTheProtectionFake() {
        FakeDataProtection protection = new FakeDataProtection();
        FakeCpfDecryption decryption = new FakeCpfDecryption();
        Cpf cpf = Cpf.of("529.982.247-25");

        assertThat(decryption.decrypt(protection.encryptCpf(cpf))).isEqualTo(cpf);
        assertThat(decryption.decryptions()).isEqualTo(1);
    }

    @Test
    void decryptionFakeRefusesWhatItDidNotEncrypt() {
        FakeCpfDecryption decryption = new FakeCpfDecryption();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decryption.decrypt(new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
