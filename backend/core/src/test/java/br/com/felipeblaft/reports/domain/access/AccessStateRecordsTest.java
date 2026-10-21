package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Estado dos registros da Fundação: invariantes de construção e ausência de dado pessoal. */
class AccessStateRecordsTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void authorshipRecordRequiresTheRoleWhenThereIsAnAuthor() {
        UUID author = UUID.randomUUID();
        assertThatThrownBy(() -> record(author, null)).isInstanceOf(IllegalArgumentException.class);
        AuthorshipRecord withRole = record(author, Role.TEACHER);
        assertThat(withRole.authorRole()).isEqualTo(Role.TEACHER);
        AuthorshipRecord unknownAuthor = record(null, null);
        assertThat(unknownAuthor.authorId()).isNull();
        assertThat(AuthorshipRecord.ATTEMPT_BLOCKED).isEqualTo("ATTEMPT_BLOCKED");
        assertThat(AuthorshipRecord.AUTHORIZATION_DENIED).isEqualTo("AUTHORIZATION_DENIED");
        assertThat(AuthorshipRecord.CPF_REFUSED_OTHER_STUDENT)
                .isEqualTo("CPF_REFUSED_OTHER_STUDENT");
        assertThat(AuthorshipRecord.TEACHER_LINK_TELEGRAM).isEqualTo("TEACHER_LINK_TELEGRAM");
    }

    @Test
    void authorshipRecordRequiresTheMandatoryFields() {
        assertThatThrownBy(
                        () ->
                                new AuthorshipRecord(
                                        UUID.randomUUID(),
                                        NOW,
                                        null,
                                        null,
                                        ActionOrigin.WEB,
                                        "ADMIN_SIGN_IN",
                                        null,
                                        null,
                                        AuthorshipResult.SUCCESS,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void administratorHidesPersonalData() {
        Administrator administrator =
                new Administrator(
                        UUID.randomUUID(),
                        "Marta Lima",
                        "marta@school.com",
                        true,
                        "$argon2id$segredo",
                        false,
                        null,
                        null,
                        null,
                        null,
                        NOW,
                        NOW);
        assertThat(administrator.toString())
                .doesNotContain("Marta")
                .doesNotContain("school.com")
                .doesNotContain("argon2");
        assertThatThrownBy(
                        () ->
                                new Administrator(
                                        UUID.randomUUID(),
                                        null,
                                        "a@b.c",
                                        true,
                                        null,
                                        false,
                                        null,
                                        null,
                                        null,
                                        null,
                                        NOW,
                                        NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void administratorSessionKeepsTheFixedExpiry() {
        UUID id = UUID.randomUUID();
        AdministratorSession session =
                new AdministratorSession(
                        id, UUID.randomUUID(), "hash", NOW, NOW.plusSeconds(7200), null, null);
        assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(7200));
        assertThat(session.toString()).contains(id.toString()).doesNotContain("hash");
        assertThatThrownBy(
                        () ->
                                new AdministratorSession(
                                        id, UUID.randomUUID(), null, NOW, NOW, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registrationDraftHidesPersonalData() {
        UUID link = UUID.randomUUID();
        EnumSet<StudentRegistrationDraft.Field> provided =
                EnumSet.of(StudentRegistrationDraft.Field.NAME);
        StudentRegistrationDraft draft =
                new StudentRegistrationDraft(
                        link,
                        StudentRegistrationDraft.Kind.CREATE,
                        null,
                        provided,
                        "Matteo Mafra",
                        "Piano",
                        LocalDate.of(2014, 5, 10),
                        new byte[] {1},
                        "cpfhash",
                        "Rita Mafra",
                        "11987654321",
                        "rita@example.com",
                        "token",
                        NOW,
                        NOW,
                        NOW.plusSeconds(1800));
        provided.clear();
        assertThat(draft.providedFields()).containsExactly(StudentRegistrationDraft.Field.NAME);
        assertThat(draft.toString())
                .contains(link.toString())
                .doesNotContain("Matteo")
                .doesNotContain("Rita")
                .doesNotContain("11987654321")
                .doesNotContain("cpfhash");
        assertThatThrownBy(
                        () ->
                                new StudentRegistrationDraft(
                                        link, null, null, provided, null, null, null, null, null,
                                        null, null, null, null, NOW, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    private AuthorshipRecord record(UUID author, Role role) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                NOW,
                author,
                role,
                ActionOrigin.WEB,
                "ADMIN_SIGN_IN",
                AuthorshipTargetType.ADMINISTRATOR,
                null,
                AuthorshipResult.SUCCESS,
                null,
                null,
                "req-1");
    }
}
