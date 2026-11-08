package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import org.junit.jupiter.api.Test;

/**
 * Edição de aluno com consentimento (FR-030): a edição que torna o aluno menor exige o
 * consentimento na mesma edição; o aluno que faz 18 anos deixa de exigir.
 */
class UpdateStudentConsentTest {

    private final ConsentRegistryScenario c = new ConsentRegistryScenario();
    private final Teacher clara = c.teacher("Clara Souza");

    private static StudentInput birthDateOnly(String birthDate) {
        return new StudentInput(
                null,
                birthDate,
                new Secret(""),
                new GuardianInput("Maria Silva", "(11) 98765-4321", "Maria@Example.com"),
                null);
    }

    private UpdateStudentUseCase.Command command(
            Student student,
            StudentInput input,
            br.com.felipeblaft.reports.domain.privacy.ConsentInput consent) {
        return new UpdateStudentUseCase.Command(
                student.id(), input, ConsentRegistryScenario.CORRELATION, consent);
    }

    @Test
    void editThatMakesTheStudentAMinorWithoutConsentIsRefusedAndNothingChanges() {
        Student adult = c.existingAdult("Adult Souza", ConsentRegistryScenario.CPF_A, clara);

        for (var actor :
                new br.com.felipeblaft.reports.domain.access.Actor[] {
                    ConsentRegistryScenario.administrator(),
                    ActorFixtures.teacherWeb(clara.id()),
                    ActorFixtures.teacherTelegram(clara.id())
                }) {
            assertThatThrownBy(
                            () ->
                                    c.update.run(
                                            actor,
                                            command(adult, birthDateOnly("2014-05-10"), null)))
                    .satisfies(
                            error ->
                                    assertThat(((DomainException) error).code())
                                            .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(c.base.students.searchById(adult.id()).orElseThrow().birthDate().getYear())
                .isEqualTo(1990);
        assertThat(c.base.recorder.ofOperation("STUDENT_UPDATE")).isEmpty();
    }

    @Test
    void editThatMakesTheStudentAMinorWithConsentSavesBothAndTheEventsShareTheTransaction() {
        Student adult = c.existingAdult("Adult Souza", ConsentRegistryScenario.CPF_A, clara);

        c.update.run(
                ActorFixtures.teacherWeb(clara.id()),
                command(adult, birthDateOnly("2014-05-10"), ConsentRegistryScenario.consent()));

        assertThat(c.base.recording.status(adult.id())).isEqualTo(ConsentStatus.VALID);
        assertThat(c.base.recorder.inTransaction())
                .extracting(r -> r.operation())
                .containsExactly("STUDENT_UPDATE", "CONSENT_RECORD");
    }

    @Test
    void editOfAMinorThatAlreadyHasConsentNeedsNoNewOne() {
        StudentView view =
                c.register.run(
                        ConsentRegistryScenario.administrator(),
                        c.registration(
                                ConsentRegistryScenario.minor(
                                        "Matteo Mafra", ConsentRegistryScenario.CPF_A),
                                ConsentRegistryScenario.consent(),
                                clara.id()));
        Student student = c.base.students.searchById(view.id()).orElseThrow();

        c.update.run(
                ConsentRegistryScenario.administrator(),
                command(
                        student,
                        new StudentInput("Matteo Mafra Jr", null, null, null, null),
                        null));

        assertThat(c.base.consents.findByStudent(student.id())).hasSize(1);
    }

    @Test
    void legacyMinorWithoutConsentCanStillHaveOtherFieldsEditedAndStaysBlockedForAi() {
        Student legacy = c.base.minor("Legacy Minor", clara);

        c.update.run(
                ActorFixtures.teacherWeb(clara.id()),
                command(
                        legacy,
                        new StudentInput(
                                null,
                                null,
                                null,
                                new GuardianInput(
                                        "Maria Silva", "(11) 98765-4321", "Maria@Example.com"),
                                "Violin"),
                        null));

        assertThat(c.base.guard.blocks(legacy.id())).isTrue();
    }

    @Test
    void invalidConsentOnEditIsRefusedBeforeSaving() {
        Student adult = c.existingAdult("Adult Souza", ConsentRegistryScenario.CPF_A, clara);

        assertThatThrownBy(
                        () ->
                                c.update.run(
                                        ConsentRegistryScenario.administrator(),
                                        command(
                                                adult,
                                                birthDateOnly("2014-05-10"),
                                                new br.com.felipeblaft.reports.domain.privacy
                                                        .ConsentInput(
                                                        "A", "X", "2099-01-01", 9, "Y"))))
                .isInstanceOf(DomainException.class);
        assertThat(c.base.consents.size()).isZero();
    }

    @Test
    void studentWhoTurnsEighteenStopsBeingBlocked() {
        Student almost =
                c.base.students.searchById(c.base.minor("Almost Adult", clara).id()).orElseThrow();
        assertThat(c.base.guard.blocks(almost.id())).isTrue();

        c.base.clock.define(java.time.Instant.parse("2032-06-01T13:00:00Z"));

        assertThat(c.base.guard.blocks(almost.id())).isFalse();
    }
}
