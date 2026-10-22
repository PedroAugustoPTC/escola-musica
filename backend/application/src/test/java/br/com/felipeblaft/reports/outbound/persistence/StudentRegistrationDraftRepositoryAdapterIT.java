package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Cadastro de aluno em andamento no Telegram (T130, FR-036): CPF só cifrado, uma linha por vínculo
 * e cascata com o vínculo por qualquer causa.
 */
class StudentRegistrationDraftRepositoryAdapterIT extends RegistryPersistenceBase {

    @Autowired StudentRegistrationDraftRepository drafts;

    private Teacher clara;
    private TelegramLink link;

    private void linkOfClara() {
        clara = teacher("Clara Souza", true);
        link = link(clara, 7_001L, 8_001L);
    }

    private StudentRegistrationDraft createDraft(UUID linkId, Instant expiresAt) {
        Cpf cpf = CanaryCpf.cpf();
        return new StudentRegistrationDraft(
                linkId,
                Kind.CREATE,
                null,
                EnumSet.of(Field.NAME, Field.BIRTH_DATE, Field.CPF, Field.GUARDIAN),
                CanaryStudent.NAME,
                CanaryStudent.INSTRUMENT,
                CanaryStudent.BIRTH_DATE,
                protection.encryptCpf(cpf),
                protection.hashCpf(cpf),
                CanaryStudent.GUARDIAN_NAME,
                CanaryStudent.GUARDIAN_PHONE_DIGITS,
                CanaryStudent.GUARDIAN_EMAIL,
                "TOKEN12345",
                now(),
                now(),
                expiresAt);
    }

    private StudentRegistrationDraft confirmationOnly(UUID linkId, Kind kind, UUID studentId) {
        return new StudentRegistrationDraft(
                linkId,
                kind,
                studentId,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "TOKENABCDE",
                now(),
                now(),
                now().plus(Duration.ofMinutes(30)));
    }

    private int count() {
        return jdbc.queryForObject(
                "select count(*) from student_registration_draft", Integer.class);
    }

    @Test
    void roundTripKeepsEveryField() {
        linkOfClara();
        Instant expires = now().plus(Duration.ofMinutes(30));
        StudentRegistrationDraft draft = createDraft(link.id(), expires);

        drafts.save(draft);

        StudentRegistrationDraft read = drafts.searchByLinkId(link.id()).orElseThrow();
        assertThat(read.linkId()).isEqualTo(link.id());
        assertThat(read.kind()).isEqualTo(Kind.CREATE);
        assertThat(read.studentId()).isNull();
        assertThat(read.providedFields())
                .containsExactlyInAnyOrder(Field.NAME, Field.BIRTH_DATE, Field.CPF, Field.GUARDIAN);
        assertThat(read.name()).isEqualTo(CanaryStudent.NAME);
        assertThat(read.instrument()).isEqualTo(CanaryStudent.INSTRUMENT);
        assertThat(read.birthDate()).isEqualTo(CanaryStudent.BIRTH_DATE);
        assertThat(read.cpfEncrypted()).isEqualTo(draft.cpfEncrypted());
        assertThat(read.cpfHash()).isEqualTo(draft.cpfHash());
        assertThat(read.guardianName()).isEqualTo(CanaryStudent.GUARDIAN_NAME);
        assertThat(read.guardianPhone()).isEqualTo(CanaryStudent.GUARDIAN_PHONE_DIGITS);
        assertThat(read.guardianEmail()).isEqualTo(CanaryStudent.GUARDIAN_EMAIL);
        assertThat(read.confirmationToken()).isEqualTo("TOKEN12345");
        assertThat(read.expiresAt()).isEqualTo(expires);
        assertThat(drafts.searchByLinkId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void cpfIsOnlyEncryptedInTheRow() {
        linkOfClara();
        drafts.save(createDraft(link.id(), now().plus(Duration.ofMinutes(30))));

        byte[] encrypted =
                jdbc.queryForObject(
                        "select cpf_encrypted from student_registration_draft", byte[].class);
        String row =
                jdbc.queryForObject(
                        "select d::text from student_registration_draft d", String.class);

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1))
                .doesNotContain(CanaryCpf.DIGITS);
        assertThat(ResponseSweeper.searching(CanaryCpf.sweepValues()).sweepText(Place.BODY, row))
                .isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select cpf_hash from student_registration_draft", String.class))
                .isEqualTo(protection.hashCpf(CanaryCpf.cpf()))
                .hasSize(64);
    }

    @Test
    void saveTwiceKeepsOneDraftPerLinkAndReplacesTheContent() {
        linkOfClara();
        drafts.save(createDraft(link.id(), now().plus(Duration.ofMinutes(30))));

        Student matteo = studentOf(clara, "Matteo Ferreira");
        drafts.save(confirmationOnly(link.id(), Kind.INACTIVATE, matteo.id()));

        assertThat(count()).isEqualTo(1);
        StudentRegistrationDraft read = drafts.searchByLinkId(link.id()).orElseThrow();
        assertThat(read.kind()).isEqualTo(Kind.INACTIVATE);
        assertThat(read.studentId()).isEqualTo(matteo.id());
        assertThat(read.providedFields()).isEmpty();
        assertThat(read.name()).isNull();
        assertThat(read.cpfEncrypted()).isNull();
        assertThat(read.cpfHash()).isNull();
    }

    @Test
    void incrementalSavesAccumulateFields() {
        linkOfClara();
        StudentRegistrationDraft started =
                new StudentRegistrationDraft(
                        link.id(),
                        Kind.CREATE,
                        null,
                        Set.of(Field.NAME),
                        "Ana Nova",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        now(),
                        now(),
                        now().plus(Duration.ofMinutes(30)));
        drafts.save(started);

        StudentRegistrationDraft next =
                new StudentRegistrationDraft(
                        link.id(),
                        Kind.CREATE,
                        null,
                        Set.of(Field.NAME, Field.BIRTH_DATE),
                        "Ana Nova",
                        null,
                        java.time.LocalDate.of(2001, 2, 3),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        started.createdAt(),
                        now().plusSeconds(60),
                        now().plus(Duration.ofMinutes(31)));
        drafts.save(next);

        StudentRegistrationDraft read = drafts.searchByLinkId(link.id()).orElseThrow();
        assertThat(read.providedFields()).containsExactlyInAnyOrder(Field.NAME, Field.BIRTH_DATE);
        assertThat(read.birthDate()).isEqualTo(java.time.LocalDate.of(2001, 2, 3));
        assertThat(read.createdAt()).isEqualTo(started.createdAt());
        assertThat(read.updatedAt()).isEqualTo(now().plusSeconds(60));
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void confirmationOnlyKindsKeepStudentAndTokenWithoutFields() {
        linkOfClara();
        Student matteo = studentOf(clara, "Matteo Ferreira");

        for (Kind kind : List.of(Kind.INACTIVATE, Kind.REACTIVATE)) {
            drafts.save(confirmationOnly(link.id(), kind, matteo.id()));
            StudentRegistrationDraft read = drafts.searchByLinkId(link.id()).orElseThrow();
            assertThat(read.kind()).isEqualTo(kind);
            assertThat(read.studentId()).isEqualTo(matteo.id());
            assertThat(read.confirmationToken()).isEqualTo("TOKENABCDE");
            assertThat(read.providedFields()).isEmpty();
        }
    }

    @Test
    void databaseRequiresStudentForUpdateInactivateAndReactivate() {
        linkOfClara();

        for (Kind kind : List.of(Kind.UPDATE, Kind.INACTIVATE, Kind.REACTIVATE)) {
            assertThatThrownBy(() -> drafts.save(confirmationOnly(link.id(), kind, null)))
                    .as(kind.name())
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(count()).isZero();
    }

    @Test
    void draftOfUnknownLinkIsRejected() {
        assertThatThrownBy(
                        () ->
                                drafts.save(
                                        createDraft(
                                                UUID.randomUUID(),
                                                now().plus(Duration.ofMinutes(30)))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- cascata

    @Test
    void draftDisappearsWhenTheLinkIsRemoved() {
        linkOfClara();
        TelegramLink other = link(teacher("Bruno Lima", true), 7_002L, 8_002L);
        drafts.save(createDraft(link.id(), now().plus(Duration.ofMinutes(30))));
        drafts.save(createDraft(other.id(), now().plus(Duration.ofMinutes(30))));

        links.remove(link.id());

        assertThat(drafts.searchByLinkId(link.id())).isEmpty();
        assertThat(drafts.searchByLinkId(other.id())).isPresent();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void draftDisappearsWhenTheLinkEndsByAnyCause() {
        linkOfClara();
        drafts.save(createDraft(link.id(), now().plus(Duration.ofMinutes(30))));

        // causa externa ao repositório (limpeza operacional, remoção do professor): o banco
        // cascateia
        jdbc.update("delete from telegram_link where id = ?", link.id());

        assertThat(count()).isZero();
        assertThat(links.searchByTeacherId(clara.id())).isEmpty();
    }

    @Test
    void removingLinkWithoutDraftIsHarmless() {
        linkOfClara();

        links.remove(link.id());

        assertThat(links.searchByTeacherId(clara.id())).isEmpty();
        assertThat(count()).isZero();
    }

    @Test
    void newLinkAfterEndStartsWithoutTheOldDraft() {
        linkOfClara();
        drafts.save(createDraft(link.id(), now().plus(Duration.ofMinutes(30))));
        links.remove(link.id());

        TelegramLink fresh = link(clara, 7_003L, 8_003L);

        assertThat(drafts.searchByLinkId(fresh.id())).isEmpty();
    }

    // ---- remoção e vencimento

    @Test
    void removeByLinkIdDeletesOnlyThatDraft() {
        linkOfClara();
        TelegramLink other = link(teacher("Bruno Lima", true), 7_002L, 8_002L);
        drafts.save(createDraft(link.id(), now().plus(Duration.ofMinutes(30))));
        drafts.save(createDraft(other.id(), now().plus(Duration.ofMinutes(30))));

        drafts.removeByLinkId(link.id());
        drafts.removeByLinkId(link.id());

        assertThat(drafts.searchByLinkId(link.id())).isEmpty();
        assertThat(drafts.searchByLinkId(other.id())).isPresent();
        assertThat(links.searchByTeacherId(clara.id())).isPresent();
    }

    @Test
    void removeExpiredDeletesOnlyThoseDueAtOrBeforeNow() {
        linkOfClara();
        TelegramLink second = link(teacher("Bruno Lima", true), 7_002L, 8_002L);
        TelegramLink third = link(teacher("Rita Prado", true), 7_003L, 8_003L);
        Instant limit = now().plus(Duration.ofMinutes(30));
        drafts.save(createDraft(link.id(), limit.minusSeconds(1)));
        drafts.save(createDraft(second.id(), limit));
        drafts.save(createDraft(third.id(), limit.plusSeconds(1)));

        int removed = drafts.removeExpired(limit);

        assertThat(removed).isEqualTo(2);
        assertThat(drafts.searchByLinkId(link.id())).isEmpty();
        assertThat(drafts.searchByLinkId(second.id())).isEmpty();
        assertThat(drafts.searchByLinkId(third.id())).isPresent();
        assertThat(drafts.removeExpired(limit)).isZero();
        assertThat(links.searchByTeacherId(clara.id())).isPresent();
    }
}
