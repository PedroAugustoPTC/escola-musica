package br.com.felipeblaft.reports.usecase.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Edição de nome e e-mail do professor; o e-mail novo segue único. */
class UpdateTeacherUseCaseTest {

    private static final String CORRELATION = "22222222-2222-4222-8222-222222222222";

    private final TeacherScenario c = new TeacherScenario();
    private final UpdateTeacherUseCase useCase =
            new UpdateTeacherUseCase(
                    c.actorAuthorization, c.teachers, c.links, c.authorship, c.clock);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
    private final Teacher bruno = c.teacher("Bruno Lima", "bruno@example.test", "BRUN2XPA");

    private UpdateTeacherUseCase.Command command(UUID id, String name, String email) {
        return new UpdateTeacherUseCase.Command(id, name, email, CORRELATION);
    }

    private static DomainException failure(ThrowingCallable action) {
        return (DomainException)
                assertThatThrownBy(action).isInstanceOf(DomainException.class).actual();
    }

    @Test
    void changesNameAndNormalizedEmail() {
        c.clock.advance(Duration.ofHours(1));

        TeacherView view =
                useCase.run(
                        ActorFixtures.administratorWeb(),
                        command(clara.id(), " Clara S. Souza ", " NOVO@Example.test "));

        assertThat(view.name()).isEqualTo("Clara S. Souza");
        assertThat(view.email()).isEqualTo("novo@example.test");
        Teacher saved = c.teachers.searchById(clara.id()).orElseThrow();
        assertThat(saved.name()).isEqualTo("Clara S. Souza");
        assertThat(saved.updatedAt()).isEqualTo(c.clock.now());
    }

    @Test
    void keepingTheOwnEmailWithAnotherCaseIsNotADuplicate() {
        TeacherView view =
                useCase.run(
                        ActorFixtures.administratorWeb(),
                        command(clara.id(), "Clara Souza", "CLARA@example.test"));

        assertThat(view.email()).isEqualTo("clara@example.test");
    }

    @Test
    void emailOfAnotherTeacherIsRefusedIncludingInactiveOnes() {
        bruno.inactivate(c.clock.now());

        DomainException error =
                failure(
                        () ->
                                useCase.run(
                                        ActorFixtures.administratorWeb(),
                                        command(clara.id(), "Clara Souza", " Bruno@example.test")));

        assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        assertThat(error.details()).containsEntry("name", "Bruno Lima");
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().email())
                .isEqualTo("clara@example.test");
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void unknownTeacherIsNotFound() {
        DomainException error =
                failure(
                        () ->
                                useCase.run(
                                        ActorFixtures.administratorWeb(),
                                        command(
                                                UUID.randomUUID(),
                                                "Clara Souza",
                                                "c@example.test")));

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void invalidDataIsRefusedAndNothingChanges() {
        DomainException error =
                failure(
                        () ->
                                useCase.run(
                                        ActorFixtures.administratorWeb(),
                                        command(clara.id(), "", "ruim")));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().name()).isEqualTo("Clara Souza");
    }

    @Test
    void reportsTelegramLinkAndRecordsAuthorship() {
        c.link(clara, 111L, 900L);

        TeacherView view =
                useCase.run(
                        ActorFixtures.administratorWeb(),
                        command(clara.id(), "Clara Souza", "clara@example.test"));

        assertThat(view.telegramLinked()).isTrue();
        AuthorshipRecord record = c.recorder.ofOperation(Operation.TEACHER_UPDATE).getFirst();
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.TEACHER);
        assertThat(record.targetId()).isEqualTo(clara.id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
    }

    @Test
    void teacherCredentialsAreDenied() {
        for (Actor actor :
                List.of(
                        ActorFixtures.teacherWeb(clara.id()),
                        ActorFixtures.teacherTelegram(clara.id()))) {
            DomainException error =
                    failure(
                            () ->
                                    useCase.run(
                                            actor,
                                            command(clara.id(), "Outro Nome", "c@example.test")));
            assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
        assertThat(clara.name()).isEqualTo("Clara Souza");
    }

    @Test
    void commandToStringHidesPersonalData() {
        assertThat(command(clara.id(), "Clara Souza", "c@example.test").toString())
                .doesNotContain("Clara")
                .doesNotContain("example");
    }
}
