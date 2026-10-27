package br.com.felipeblaft.reports.usecase.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Inativar corta o acesso na hora e nunca é bloqueado por relatório; reativar emite código novo.
 */
class InactivateAndReactivateTeacherUseCaseTest {

    private static final String CORRELATION = "33333333-3333-4333-8333-333333333333";

    private final TeacherScenario c = new TeacherScenario();
    private final InactivateTeacherUseCase inactivate =
            new InactivateTeacherUseCase(
                    c.actorAuthorization,
                    c.teachers,
                    c.revocation,
                    c.termination,
                    c.authorship,
                    c.clock);
    private final ReactivateTeacherUseCase reactivate =
            new ReactivateTeacherUseCase(
                    c.actorAuthorization, c.teachers, c.issuance, c.authorship, c.clock);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");

    private InactivateTeacherUseCase.Command inactivation(UUID id) {
        return new InactivateTeacherUseCase.Command(id, CORRELATION);
    }

    private ReactivateTeacherUseCase.Command reactivation(UUID id) {
        return new ReactivateTeacherUseCase.Command(id, CORRELATION);
    }

    private static DomainException failure(ThrowingCallable action) {
        return (DomainException)
                assertThatThrownBy(action).isInstanceOf(DomainException.class).actual();
    }

    private WebSession webSession(Teacher teacher, String hash) {
        WebSession session =
                WebSession.create(teacher.id(), hash, c.clock.now(), Duration.ofHours(12));
        c.sessions.save(session);
        return session;
    }

    @Test
    void inactivatingCutsEverythingAtOnce() {
        WebSession session = webSession(clara, "h1");
        TelegramLink link = c.link(clara, 111L, 900L);
        c.draft(link);
        c.pendingAction("a1", 900L);

        TeacherView view =
                inactivate.run(ActorFixtures.administratorWeb(), inactivation(clara.id()));

        assertThat(view.active()).isFalse();
        Teacher saved = c.teachers.searchById(clara.id()).orElseThrow();
        assertThat(saved.active()).isFalse();
        assertThat(saved.accessCodeHash()).isNull();
        assertThat(c.teachers.searchByAccessCodeHash("code:K7QM2XPA")).isEmpty();
        assertThat(c.sessions.revocationOf(session.id()).orElseThrow().reason())
                .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED);
        assertThat(c.sessions.all()).hasSize(1);
        assertThat(c.links.all()).isEmpty();
        assertThat(c.drafts.size()).isZero();
        assertThat(c.actions.stored("a1").state()).isEqualTo(ChannelActionState.DISCARDED);
    }

    @Test
    void isNeverBlockedByOpenReportsAndTouchesNoReport() {
        Student student = c.student(clara, "Aluno Teste", "Violão");
        c.open.add(
                clara.id(),
                new OpenReport(
                        ReportType.LESSON,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        student.id(),
                        "Aluno Teste",
                        ReportStatus.AWAITING_REVIEW,
                        JobStep.STRUCTURING,
                        null,
                        Channel.WEB,
                        c.clock.now()));
        var before = c.open.listByTeacher(clara.id());

        TeacherView view =
                inactivate.run(ActorFixtures.administratorWeb(), inactivation(clara.id()));

        assertThat(view.active()).isFalse();
        assertThat(c.open.listByTeacher(clara.id())).isEqualTo(before);
        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
    }

    @Test
    void recordsAuthorshipAndInactivatingTwiceIsHarmless() {
        inactivate.run(ActorFixtures.administratorWeb(), inactivation(clara.id()));

        TeacherView again =
                inactivate.run(ActorFixtures.administratorWeb(), inactivation(clara.id()));

        assertThat(again.active()).isFalse();
        AuthorshipRecord record = c.recorder.ofOperation(Operation.TEACHER_INACTIVATE).getFirst();
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.TEACHER);
        assertThat(record.targetId()).isEqualTo(clara.id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(c.recorder.ofOperation(Operation.TEACHER_INACTIVATE)).hasSize(1);
    }

    @Test
    void unknownTeacherIsNotFoundAndTeachersAreDenied() {
        assertThat(
                        failure(
                                        () ->
                                                inactivate.run(
                                                        ActorFixtures.administratorWeb(),
                                                        inactivation(UUID.randomUUID())))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(
                        failure(
                                        () ->
                                                inactivate.run(
                                                        ActorFixtures.teacherWeb(clara.id()),
                                                        inactivation(clara.id())))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().active()).isTrue();
    }

    @Test
    void reactivatingIssuesANewCodeAndKeepsAssociationsButNotSessionsOrLink() {
        Student student = c.student(clara, "Aluno Teste", "Violão");
        WebSession session = webSession(clara, "h1");
        c.link(clara, 111L, 900L);
        inactivate.run(ActorFixtures.administratorWeb(), inactivation(clara.id()));
        c.generator.schedule("NEWC2XPA");
        c.clock.advance(Duration.ofDays(2));

        TeacherWithCode result =
                reactivate.run(ActorFixtures.administratorWeb(), reactivation(clara.id()));

        assertThat(result.teacher().active()).isTrue();
        assertThat(result.accessCode().value()).isEqualTo("NEWC2XPA");
        assertThat(result.validUntil()).isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        Teacher saved = c.teachers.searchById(clara.id()).orElseThrow();
        assertThat(saved.active()).isTrue();
        assertThat(saved.validCodeAt(c.clock.now())).isTrue();
        assertThat(c.teachers.searchByAccessCodeHash("code:K7QM2XPA")).isEmpty();
        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
        assertThat(c.sessions.isRevoked(session.id())).isTrue();
        assertThat(c.links.all()).isEmpty();
        assertThat(result.teacher().telegramLinked()).isFalse();
    }

    @Test
    void reactivationRecordsAuthorshipWithoutTheCode() {
        inactivate.run(ActorFixtures.administratorWeb(), inactivation(clara.id()));

        TeacherWithCode result =
                reactivate.run(ActorFixtures.administratorWeb(), reactivation(clara.id()));

        AuthorshipRecord record = c.recorder.ofOperation(Operation.TEACHER_REACTIVATE).getFirst();
        assertThat(record.targetId()).isEqualTo(clara.id());
        assertThat(record.toString()).doesNotContain(result.accessCode().value());
    }

    @Test
    void reactivatingAnUnknownTeacherIsNotFoundAndTeachersAreDenied() {
        assertThat(
                        failure(
                                        () ->
                                                reactivate.run(
                                                        ActorFixtures.administratorWeb(),
                                                        reactivation(UUID.randomUUID())))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(
                        failure(
                                        () ->
                                                reactivate.run(
                                                        ActorFixtures.teacherTelegram(clara.id()),
                                                        reactivation(clara.id())))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void reactivatingAnActiveTeacherReplacesTheCodeWithoutTouchingSessions() {
        WebSession session = webSession(clara, "h1");
        c.generator.schedule("NEWC2XPA");

        TeacherWithCode result =
                reactivate.run(ActorFixtures.administratorWeb(), reactivation(clara.id()));

        assertThat(result.accessCode().value()).isEqualTo("NEWC2XPA");
        assertThat(c.sessions.isRevoked(session.id())).isFalse();
    }
}
