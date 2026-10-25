package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coleta campo a campo do cadastro em andamento: valida no núcleo e guarda o CPF só cifrado. */
class CollectStudentRegistrationFieldUseCaseTest {

    private static final String MINOR_BIRTH = "10/05/2014";
    private static final String ADULT_BIRTH = "15/03/1990";

    private final DraftScenario scenario = new DraftScenario();

    private CollectStudentRegistrationFieldUseCase.Result collect(Field field, String text) {
        return scenario.collect()
                .run(
                        scenario.actor,
                        new CollectStudentRegistrationFieldUseCase.Command(
                                scenario.link.id(), field, text));
    }

    private DomainException refused(Field field, String text) {
        try {
            collect(field, text);
        } catch (DomainException error) {
            return error;
        }
        throw new AssertionError("esperava DomainException");
    }

    private StudentRegistrationDraft draft() {
        return scenario.drafts.searchByLinkId(scenario.link.id()).orElseThrow();
    }

    private void startCreate() {
        scenario.startDraft(Kind.CREATE, null);
    }

    private void guardian(String name, String phone, String email) {
        collect(Field.GUARDIAN, name);
        collect(Field.GUARDIAN, phone);
        collect(Field.GUARDIAN, email);
    }

    // ---- Roteiro de cadastro

    @Test
    void anAdultIsAskedNameBirthDateCpfAndInstrumentThenGetsTheSummary() {
        startCreate();

        var name = collect(Field.NAME, "  Ana Souza ");
        assertThat(name.nextField()).isEqualTo(Field.BIRTH_DATE);
        assertThat(name.guardianRequired()).isFalse();
        assertThat(name.summary()).isNull();
        assertThat(name.confirmationToken()).isNull();

        var birth = collect(Field.BIRTH_DATE, ADULT_BIRTH);
        assertThat(birth.nextField()).isEqualTo(Field.CPF);
        assertThat(birth.guardianRequired()).isFalse();

        var cpf = collect(Field.CPF, DraftScenario.CPF);
        assertThat(cpf.nextField()).isEqualTo(Field.INSTRUMENT);
        assertThat(cpf.guardianRequired()).isFalse();

        var instrument = collect(Field.INSTRUMENT, "Violão");
        assertThat(instrument.nextField()).isNull();
        assertThat(instrument.summary()).isNotNull();
        assertThat(instrument.summary().name()).isEqualTo("Ana Souza");
        assertThat(instrument.summary().birthDate()).isEqualTo(LocalDate.of(1990, 3, 15));
        assertThat(instrument.summary().instrument()).isEqualTo("Violão");
        assertThat(instrument.summary().guardianName()).isNull();
        assertThat(instrument.confirmationToken()).matches("[A-Z2-9]{8}");
        assertThat(draft().confirmationToken()).isEqualTo(instrument.confirmationToken());
        assertThat(scenario.students.all()).isEmpty();
        assertThat(scenario.writes()).isZero();
    }

    @Test
    void aMinorIsAskedForTheGuardianPartByPartBeforeTheInstrument() {
        startCreate();
        collect(Field.NAME, "Pedro");

        var birth = collect(Field.BIRTH_DATE, MINOR_BIRTH);
        assertThat(birth.guardianRequired()).isTrue();
        assertThat(birth.nextField()).isEqualTo(Field.CPF);

        var cpf = collect(Field.CPF, DraftScenario.CPF);
        assertThat(cpf.guardianRequired()).isTrue();
        assertThat(cpf.nextField()).isEqualTo(Field.GUARDIAN);

        assertThat(collect(Field.GUARDIAN, "Maria Souza").nextField()).isEqualTo(Field.GUARDIAN);
        assertThat(collect(Field.GUARDIAN, "(11) 91234-5678").nextField())
                .isEqualTo(Field.GUARDIAN);
        var email = collect(Field.GUARDIAN, "Maria@Example.com");
        assertThat(email.nextField()).isEqualTo(Field.INSTRUMENT);
        assertThat(email.guardianRequired()).isTrue();

        var instrument = collect(Field.INSTRUMENT, "Piano");
        assertThat(instrument.nextField()).isNull();
        assertThat(instrument.summary().guardianName()).isEqualTo("Maria Souza");
        assertThat(draft().guardianPhone()).isEqualTo("11912345678");
        assertThat(draft().guardianEmail()).isEqualTo("maria@example.com");
    }

    @Test
    void guardianRequiredUsesTodayInTheSchoolTimezone() {
        // 21/09/2026 23:00 em São Paulo já é 22/09 em UTC; faz 18 anos em 22/09/2026
        scenario.clock.define(Instant.parse("2026-09-22T02:00:00Z"));
        startCreate();
        collect(Field.NAME, "Pedro");

        var minorYet = collect(Field.BIRTH_DATE, "22/09/2008");
        assertThat(minorYet.guardianRequired()).isTrue();

        var adult = collect(Field.BIRTH_DATE, "21/09/2008");
        assertThat(adult.guardianRequired()).isFalse();
        assertThat(adult.nextField()).isEqualTo(Field.CPF);
    }

    @Test
    void theInstrumentCanBeSkippedInACreate() {
        startCreate();
        collect(Field.NAME, "Ana");
        collect(Field.BIRTH_DATE, ADULT_BIRTH);
        collect(Field.CPF, DraftScenario.CPF);

        var skipped = collect(Field.INSTRUMENT, "   ");

        assertThat(skipped.nextField()).isNull();
        assertThat(skipped.summary().instrument()).isNull();
        assertThat(draft().instrument()).isNull();
        assertThat(draft().providedFields()).contains(Field.INSTRUMENT);
    }

    @Test
    void aFieldCanBeAnsweredAgainWithoutLosingTheOthers() {
        startCreate();
        collect(Field.NAME, "Ana");
        collect(Field.BIRTH_DATE, ADULT_BIRTH);

        var again = collect(Field.NAME, "Ana Beatriz");

        assertThat(draft().name()).isEqualTo("Ana Beatriz");
        assertThat(draft().birthDate()).isEqualTo(LocalDate.of(1990, 3, 15));
        assertThat(again.nextField()).isEqualTo(Field.CPF);
    }

    // ---- CPF

    @Test
    void theCpfIsKeptOnlyEncryptedAndAsHash() {
        startCreate();

        collect(Field.CPF, DraftScenario.CPF);

        Cpf cpf = Cpf.of(DraftScenario.CPF);
        assertThat(draft().cpfEncrypted()).isEqualTo(scenario.protection.encryptCpf(cpf));
        assertThat(draft().cpfHash()).isEqualTo(scenario.protection.hashCpf(cpf));
        assertThat(draft().toString()).doesNotContain("529").doesNotContain("52998224725");
        assertThat(scenario.decryption.decryptions()).isZero();
    }

    @Test
    void theSummaryAndTheResultCarryNoCpfOrContact() {
        startCreate();
        collect(Field.NAME, "Pedro");
        collect(Field.BIRTH_DATE, MINOR_BIRTH);
        collect(Field.CPF, DraftScenario.CPF);
        guardian("Maria Souza", "(11) 91234-5678", "maria@example.com");
        var done = collect(Field.INSTRUMENT, "Piano");

        assertThat(
                        java.util.Arrays.stream(
                                        CollectStudentRegistrationFieldUseCase.Summary.class
                                                .getRecordComponents())
                                .map(c -> c.getName()))
                .containsExactly("name", "birthDate", "instrument", "guardianName");
        assertThat(done.toString())
                .doesNotContain("Pedro")
                .doesNotContain(done.confirmationToken());
        assertThat(done.summary().toString()).doesNotContain("Pedro", "Maria", "2014");
    }

    @Test
    void anInvalidCpfIsRefusedWithoutRepeatingTheValueAndSavesNothing() {
        startCreate();
        int saves = scenario.drafts.saves();

        DomainException error = refused(Field.CPF, "529.982.247-26");

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details())
                .isEqualTo(
                        Map.of("fields", List.of(Map.of("field", "cpf", "code", "INVALID_CPF"))));
        assertThat(error.getMessage()).doesNotContain("529");
        assertThat(error.details().toString()).doesNotContain("529").doesNotContain("247");
        assertThat(scenario.drafts.saves()).isEqualTo(saves);
        assertThat(draft().providedFields()).isEmpty();
    }

    // ---- Validação campo a campo

    @Test
    void eachFieldIsRefusedWithItsOwnReason() {
        startCreate();

        assertViolation(refused(Field.NAME, "A"), "name", "INVALID_NAME");
        assertViolation(refused(Field.BIRTH_DATE, "31/02/2010"), "birthDate", "INVALID_BIRTH_DATE");
        assertViolation(refused(Field.BIRTH_DATE, "01/01/2999"), "birthDate", "INVALID_BIRTH_DATE");
        assertViolation(refused(Field.BIRTH_DATE, "abc"), "birthDate", "INVALID_BIRTH_DATE");
        assertViolation(
                refused(Field.INSTRUMENT, "x".repeat(61)), "instrument", "INVALID_INSTRUMENT");
        assertViolation(refused(Field.GUARDIAN, ""), "guardian.name", "INVALID_NAME");
        collect(Field.GUARDIAN, "Maria Souza");
        assertViolation(refused(Field.GUARDIAN, "123"), "guardian.phone", "INVALID_PHONE");
        collect(Field.GUARDIAN, "11912345678");
        assertViolation(refused(Field.GUARDIAN, "sem-arroba"), "guardian.email", "INVALID_EMAIL");
        assertThat(draft().providedFields()).isEmpty();
    }

    private static void assertViolation(DomainException error, String field, String code) {
        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details())
                .isEqualTo(Map.of("fields", List.of(Map.of("field", field, "code", code))));
    }

    @Test
    void aMissingTextIsRefusedAsInvalid() {
        startCreate();

        assertViolation(refused(Field.NAME, null), "name", "INVALID_NAME");
        assertViolation(refused(Field.CPF, null), "cpf", "INVALID_CPF");
    }

    @Test
    void aRefusedAnswerKeepsTheDeadlineAndTheTokenAsTheyWere() {
        startCreate();
        StudentRegistrationDraft before = draft();
        scenario.clock.advance(Duration.ofMinutes(10));

        refused(Field.NAME, "A");

        assertThat(draft()).isEqualTo(before);
    }

    // ---- Edição

    @Test
    void anUpdateWithAFieldGoesStraightToTheSummaryWithOnlyWhatChanges() {
        scenario.startDraft(Kind.UPDATE, scenario.ownStudent().id());

        var result = collect(Field.NAME, "Novo Nome");

        assertThat(result.nextField()).isNull();
        assertThat(result.guardianRequired()).isFalse();
        assertThat(result.summary().name()).isEqualTo("Novo Nome");
        assertThat(result.summary().birthDate()).isNull();
        assertThat(result.summary().instrument()).isNull();
        assertThat(result.summary().guardianName()).isNull();
        assertThat(result.confirmationToken()).matches("[A-Z2-9]{8}");
    }

    @Test
    void anUpdateThatMakesTheStudentAMinorAsksForTheGuardianBeforeTheSummary() {
        scenario.startDraft(Kind.UPDATE, scenario.ownStudent().id());

        var birth = collect(Field.BIRTH_DATE, MINOR_BIRTH);
        assertThat(birth.guardianRequired()).isTrue();
        assertThat(birth.nextField()).isEqualTo(Field.GUARDIAN);
        assertThat(birth.summary()).isNull();
        assertThat(birth.confirmationToken()).isNull();

        collect(Field.GUARDIAN, "Maria Souza");
        collect(Field.GUARDIAN, "11912345678");
        var done = collect(Field.GUARDIAN, "maria@example.com");

        assertThat(done.nextField()).isNull();
        assertThat(done.summary().guardianName()).isEqualTo("Maria Souza");
        assertThat(done.summary().birthDate()).isEqualTo(LocalDate.of(2014, 5, 10));
        assertThat(done.confirmationToken()).isNotNull();
    }

    @Test
    void anUpdateCpfIsAlsoKeptOnlyEncrypted() {
        scenario.startDraft(Kind.UPDATE, scenario.ownStudent().id());

        var result = collect(Field.CPF, DraftScenario.CPF);

        assertThat(result.summary()).isNotNull();
        assertThat(draft().cpfEncrypted())
                .isEqualTo(scenario.protection.encryptCpf(Cpf.of(DraftScenario.CPF)));
    }

    @Test
    void anUpdateCannotClearTheInstrumentWithABlankText() {
        scenario.startDraft(Kind.UPDATE, scenario.ownStudent().id());

        assertViolation(refused(Field.INSTRUMENT, " "), "instrument", "INVALID_INSTRUMENT");
        assertThat(draft().providedFields()).isEmpty();
    }

    @Test
    void restartingTheGuardianStartsOverFromTheName() {
        scenario.startDraft(Kind.UPDATE, scenario.ownStudent().id());
        collect(Field.BIRTH_DATE, MINOR_BIRTH);
        guardian("Maria Souza", "11912345678", "maria@example.com");

        collect(Field.GUARDIAN, "Outro Responsável");

        assertThat(draft().guardianName()).isEqualTo("Outro Responsável");
        assertThat(draft().guardianPhone()).isNull();
        assertThat(draft().guardianEmail()).isNull();
    }

    // ---- Token e prazo

    @Test
    void everySummaryGetsANewTokenAndTheOldOneStopsWorking() {
        scenario.startDraft(Kind.UPDATE, scenario.ownStudent().id());
        var first = collect(Field.NAME, "Nome Um");

        var second = collect(Field.NAME, "Nome Dois");

        assertThat(second.confirmationToken()).isNotEqualTo(first.confirmationToken());
        assertThat(draft().matchesToken(first.confirmationToken())).isFalse();
        assertThat(draft().matchesToken(second.confirmationToken())).isTrue();
    }

    @Test
    void changingAFieldAfterTheSummaryDropsTheTokenUntilTheNextSummary() {
        startCreate();
        collect(Field.NAME, "Ana");
        collect(Field.BIRTH_DATE, ADULT_BIRTH);
        collect(Field.CPF, DraftScenario.CPF);
        var done = collect(Field.INSTRUMENT, "Violão");
        assertThat(done.confirmationToken()).isNotNull();

        var changed = collect(Field.BIRTH_DATE, MINOR_BIRTH);

        assertThat(changed.nextField()).isEqualTo(Field.GUARDIAN);
        assertThat(changed.confirmationToken()).isNull();
        assertThat(draft().confirmationToken()).isNull();
    }

    @Test
    void changingTheBirthDateToAnAdultDiscardsTheGuardianAlreadyGiven() {
        startCreate();
        collect(Field.NAME, "Pedro");
        collect(Field.BIRTH_DATE, MINOR_BIRTH);
        collect(Field.CPF, DraftScenario.CPF);
        guardian("Maria Souza", "11912345678", "maria@example.com");
        collect(Field.INSTRUMENT, "Piano");

        var adult = collect(Field.BIRTH_DATE, ADULT_BIRTH);

        assertThat(adult.guardianRequired()).isFalse();
        assertThat(adult.nextField()).isNull();
        assertThat(adult.confirmationToken()).isNotNull();
        assertThat(adult.summary().guardianName()).isNull();
        assertThat(draft().guardianName()).isNull();
        assertThat(draft().guardianPhone()).isNull();
        assertThat(draft().guardianEmail()).isNull();
    }

    @Test
    void everyAcceptedAnswerRenewsTheInactivityDeadline() {
        startCreate();
        scenario.clock.advance(Duration.ofMinutes(20));

        collect(Field.NAME, "Ana");

        assertThat(draft().expiresAt())
                .isEqualTo(scenario.clock.now().plus(DraftScenario.VALIDITY));
        scenario.clock.advance(Duration.ofMinutes(20));
        assertThat(collect(Field.BIRTH_DATE, ADULT_BIRTH).nextField()).isEqualTo(Field.CPF);
    }

    @Test
    void anExpiredDraftIsDiscardedAndRefused() {
        startCreate();
        scenario.clock.advance(DraftScenario.VALIDITY);

        DomainException error = refused(Field.NAME, "Ana");

        assertThat(error.code()).isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);
        assertThat(scenario.drafts.all()).isEmpty();
    }

    @Test
    void withoutADraftTheAnswerIsRefusedAsExpired() {
        assertThat(refused(Field.NAME, "Ana").code()).isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);
    }

    @Test
    void aStatusDraftHasNoFieldsToCollect() {
        scenario.startDraft(Kind.INACTIVATE, scenario.ownStudent().id());

        assertThat(refused(Field.NAME, "Ana").code()).isEqualTo(ErrorCode.CALLBACK_EXPIRED);
        assertThat(draft().providedFields()).isEmpty();
    }

    // ---- Autorização

    @Test
    void onlyTheTeacherOnTelegramMayCollect() {
        startCreate();
        for (Actor denied :
                new Actor[] {
                    ActorFixtures.administratorWeb(),
                    Actor.teacher(scenario.teacher.id(), ActionOrigin.WEB),
                    ActorFixtures.initialLoad()
                }) {
            try {
                scenario.collect()
                        .run(
                                denied,
                                new CollectStudentRegistrationFieldUseCase.Command(
                                        scenario.link.id(), Field.NAME, "Ana"));
                throw new AssertionError("esperava recusa");
            } catch (DomainException error) {
                assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
        assertThat(draft().providedFields()).isEmpty();
    }

    @Test
    void anotherLinksDraftIsNeverTouched() {
        startCreate();
        UUID other = UUID.randomUUID();
        try {
            scenario.collect()
                    .run(
                            scenario.actor,
                            new CollectStudentRegistrationFieldUseCase.Command(
                                    other, Field.NAME, "Ana"));
            throw new AssertionError("esperava recusa");
        } catch (DomainException error) {
            assertThat(error.code()).isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);
        }
        assertThat(draft().providedFields()).isEmpty();
    }

    @Test
    void commandNeverRevealsTheTypedText() {
        var command =
                new CollectStudentRegistrationFieldUseCase.Command(
                        UUID.randomUUID(), Field.CPF, "52998224725");

        assertThat(command.toString()).doesNotContain("52998224725");
    }
}
