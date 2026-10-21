package br.com.felipeblaft.reports.usecase.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Cadastro de professor pelo administrador (e pela carga inicial): nasce ativo, com código. */
class RegisterTeacherUseCaseTest {

    private static final String CORRELATION = "11111111-1111-4111-8111-111111111111";
    private static final Pattern CODE_FORMAT = Pattern.compile("[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{8}");

    private final TeacherScenario c = new TeacherScenario();
    private final RegisterTeacherUseCase useCase =
            new RegisterTeacherUseCase(
                    c.actorAuthorization, c.teachers, c.issuance, c.authorship, c.clock);

    private RegisterTeacherUseCase.Command command(String name, String email) {
        return new RegisterTeacherUseCase.Command(name, email, CORRELATION);
    }

    private static DomainException failure(Runnable action) {
        return (DomainException)
                assertThatThrownBy(action::run).isInstanceOf(DomainException.class).actual();
    }

    @Test
    void bornActiveWithAnEightCharacterCodeValidForSevenDays() {
        TeacherWithCode result =
                useCase.run(
                        ActorFixtures.administratorWeb(),
                        command("  Clara Souza ", "  Clara@Example.TEST "));

        assertThat(result.teacher().name()).isEqualTo("Clara Souza");
        assertThat(result.teacher().email()).isEqualTo("clara@example.test");
        assertThat(result.teacher().active()).isTrue();
        assertThat(result.teacher().telegramLinked()).isFalse();
        assertThat(result.accessCode().value()).matches(CODE_FORMAT);
        assertThat(result.validUntil()).isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        Teacher saved = c.teachers.searchById(result.teacher().id()).orElseThrow();
        assertThat(saved.active()).isTrue();
        assertThat(saved.validCodeAt(c.clock.now())).isTrue();
    }

    @Test
    void storesOnlyTheCodeHashAndTheCodeWorksToSignIn() {
        TeacherWithCode result =
                useCase.run(
                        ActorFixtures.administratorWeb(), command("Clara Souza", "c@example.test"));

        Teacher saved = c.teachers.searchById(result.teacher().id()).orElseThrow();
        assertThat(saved.accessCodeHash())
                .isEqualTo(c.protection.hashAccessCode(AccessCode.of(result.accessCode().value())));
        assertThat(saved.accessCodeHash()).doesNotContain(result.accessCode().value() + "x");
        assertThat(result.accessCode().toString()).doesNotContain(result.accessCode().value());
        assertThat(result.teacher().toString()).doesNotContain("Clara");
    }

    @Test
    void duplicateEmailIgnoresCaseAndSpacesIncludingInactiveTeachersAndNamesTheOwner() {
        Teacher inactive = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
        inactive.inactivate(c.clock.now());
        c.teachers.save(inactive);

        DomainException error =
                failure(
                        () ->
                                useCase.run(
                                        ActorFixtures.administratorWeb(),
                                        command("Outra Pessoa", "  CLARA@example.TEST ")));

        assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        assertThat(error.details()).containsEntry("name", "Clara Souza");
        assertThat(c.teachers.all()).hasSize(1);
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void invalidNameAndEmailAreRefusedWithEveryViolation() {
        DomainException error =
                failure(() -> useCase.run(ActorFixtures.administratorWeb(), command("A", "nope")));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().toString()).contains("INVALID_NAME").contains("INVALID_EMAIL");
        assertThat(error.details().toString()).doesNotContain("nope");
        assertThat(c.teachers.all()).isEmpty();
    }

    @Test
    void recordsAuthorshipWithoutTheCode() {
        TeacherWithCode result =
                useCase.run(
                        ActorFixtures.administratorWeb(), command("Clara Souza", "c@example.test"));

        List<AuthorshipRecord> records = c.recorder.ofOperation(Operation.TEACHER_REGISTER);
        assertThat(records).hasSize(1);
        AuthorshipRecord record = records.getFirst();
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.authorId()).isEqualTo(ActorFixtures.ADMINISTRATOR_ID);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.TEACHER);
        assertThat(record.targetId()).isEqualTo(result.teacher().id());
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.toString()).doesNotContain(result.accessCode().value());
        assertThat(c.recorder.inTransaction()).hasSize(1);
    }

    @Test
    void failureToRecordAuthorshipFailsTheOperation() {
        c.recorder.failNextRecordWith(new IllegalStateException("falha ao gravar"));

        assertThatThrownBy(
                        () ->
                                useCase.run(
                                        ActorFixtures.administratorWeb(),
                                        command("Clara Souza", "c@example.test")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theInitialLoadMayRegisterWithTheSystemActor() {
        TeacherWithCode result =
                useCase.run(
                        ActorFixtures.initialLoad(),
                        new RegisterTeacherUseCase.Command(
                                "Clara Souza", "c@example.test", "load-abc"));

        assertThat(result.teacher().active()).isTrue();
        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.authorId()).isNull();
        assertThat(record.authorRole()).isNull();
        assertThat(record.origin().name()).isEqualTo("INITIAL_LOAD");
    }

    @Test
    void teachersAndUnauthenticatedActorsCannotRegister() {
        for (var actor : List.of(ActorFixtures.teacherWeb(), ActorFixtures.teacherTelegram())) {
            DomainException error =
                    failure(() -> useCase.run(actor, command("Clara Souza", "c@example.test")));
            assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
        assertThat(c.teachers.all()).isEmpty();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void administratorWithProvisionalPasswordCannotRegister() {
        DomainException error =
                failure(
                        () ->
                                useCase.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        command("Clara Souza", "c@example.test")));

        assertThat(error.code()).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void commandToStringHidesNameAndEmail() {
        assertThat(command("Clara Souza", "c@example.test").toString())
                .doesNotContain("Clara")
                .doesNotContain("example");
    }
}
