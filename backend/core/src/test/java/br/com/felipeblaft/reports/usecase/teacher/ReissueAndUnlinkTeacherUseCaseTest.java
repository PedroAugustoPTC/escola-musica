package br.com.felipeblaft.reports.usecase.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.access.LinkTelegramUseCase;
import java.time.Duration;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Reemissão do código e desvinculação do Telegram pelo administrador (US6). */
class ReissueAndUnlinkTeacherUseCaseTest {

    private static final String CORRELATION = "44444444-4444-4444-8444-444444444444";

    private final TeacherScenario c = new TeacherScenario();
    private final ReissueAccessCodeUseCase reissue =
            new ReissueAccessCodeUseCase(
                    c.actorAuthorization,
                    c.teachers,
                    c.issuance,
                    c.revocation,
                    c.termination,
                    c.authorship,
                    c.clock);
    private final UnlinkTeacherTelegramUseCase unlink =
            new UnlinkTeacherTelegramUseCase(
                    c.actorAuthorization, c.teachers, c.termination, c.authorship);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");

    private ReissueAccessCodeUseCase.Command reissuing(boolean endLink) {
        return new ReissueAccessCodeUseCase.Command(clara.id(), endLink, CORRELATION);
    }

    private static DomainException failure(ThrowingCallable action) {
        return (DomainException)
                assertThatThrownBy(action).isInstanceOf(DomainException.class).actual();
    }

    private WebSession webSession(String hash) {
        WebSession session =
                WebSession.create(clara.id(), hash, c.clock.now(), Duration.ofHours(12));
        c.sessions.save(session);
        return session;
    }

    @Test
    void oldCodeDiesAtOnceAndNewCodeIsShownOnceWithSevenDays() {
        c.generator.schedule("NEWC2XPA");
        c.clock.advance(Duration.ofDays(3));

        TeacherWithCode result = reissue.run(ActorFixtures.administratorWeb(), reissuing(false));

        assertThat(result.accessCode().value()).isEqualTo("NEWC2XPA");
        assertThat(result.validUntil()).isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(c.teachers.searchByAccessCodeHash("code:K7QM2XPA")).isEmpty();
        assertThat(c.teachers.searchByAccessCodeHash("code:NEWC2XPA")).isPresent();
        assertThat(result.teacher().toString()).doesNotContain("NEWC2XPA");
    }

    @Test
    void webSessionsAreRevokedWithCodeReissuedReason() {
        WebSession session = webSession("h1");

        reissue.run(ActorFixtures.administratorWeb(), reissuing(false));

        assertThat(c.sessions.revocationOf(session.id()).orElseThrow().reason())
                .isEqualTo(SessionRevocationReason.CODE_REISSUED);
    }

    @Test
    void linkIsKeptWithoutTheOptionAndKeepsTheDraft() {
        TelegramLink link = c.link(clara, 111L, 900L);
        c.draft(link);
        c.pendingAction("a1", 900L);

        TeacherWithCode result = reissue.run(ActorFixtures.administratorWeb(), reissuing(false));

        assertThat(result.teacher().telegramLinked()).isTrue();
        assertThat(c.links.searchByTeacherId(clara.id())).isPresent();
        assertThat(c.drafts.size()).isEqualTo(1);
        assertThat(c.actions.stored("a1").state()).isEqualTo(ChannelActionState.PENDING);
    }

    @Test
    void linkDraftAndPendingActionsEndWithTheOption() {
        TelegramLink link = c.link(clara, 111L, 900L);
        c.draft(link);
        c.pendingAction("a1", 900L);

        TeacherWithCode result = reissue.run(ActorFixtures.administratorWeb(), reissuing(true));

        assertThat(result.teacher().telegramLinked()).isFalse();
        assertThat(c.links.all()).isEmpty();
        assertThat(c.drafts.size()).isZero();
        assertThat(c.actions.stored("a1").state()).isEqualTo(ChannelActionState.DISCARDED);
    }

    @Test
    void inactiveTeacherIsRefusedGuidingReactivation() {
        clara.inactivate(c.clock.now());

        DomainException error =
                failure(() -> reissue.run(ActorFixtures.administratorWeb(), reissuing(true)));

        assertThat(error.code()).isEqualTo(ErrorCode.TEACHER_INACTIVE);
        assertThat(clara.accessCodeHash()).isNull();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void unknownTeacherAndTeacherCredentialsAreDenied() {
        assertThat(
                        failure(
                                        () ->
                                                reissue.run(
                                                        ActorFixtures.administratorWeb(),
                                                        new ReissueAccessCodeUseCase.Command(
                                                                UUID.randomUUID(),
                                                                false,
                                                                CORRELATION)))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(
                        failure(
                                        () ->
                                                reissue.run(
                                                        ActorFixtures.teacherWeb(clara.id()),
                                                        reissuing(false)))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(clara.accessCodeHash()).isEqualTo("code:K7QM2XPA");
    }

    @Test
    void reissueRecordsAuthorshipWithoutTheCode() {
        TeacherWithCode result = reissue.run(ActorFixtures.administratorWeb(), reissuing(false));

        AuthorshipRecord record =
                c.recorder.ofOperation(Operation.TEACHER_REISSUE_ACCESS_CODE).getFirst();
        assertThat(record.targetId()).isEqualTo(clara.id());
        assertThat(record.toString()).doesNotContain(result.accessCode().value());
    }

    @Test
    void unlinkEndsLinkDraftAndPendingActionsAndTeacherNeedsAValidCodeToLinkAgain() {
        TelegramLink link = c.link(clara, 111L, 900L);
        c.draft(link);
        c.pendingAction("a1", 900L);

        unlink.run(
                ActorFixtures.administratorWeb(),
                new UnlinkTeacherTelegramUseCase.Command(clara.id(), CORRELATION));

        assertThat(c.links.all()).isEmpty();
        assertThat(c.drafts.size()).isZero();
        assertThat(c.actions.stored("a1").state()).isEqualTo(ChannelActionState.DISCARDED);
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().active()).isTrue();
        LinkTelegramUseCase relink =
                new LinkTelegramUseCase(
                        c.authentication, c.renewal, c.links, c.authorship, c.protection, c.clock);
        assertThat(failure(() -> relink.run(111L, 900L, "WRONG222")).code())
                .isEqualTo(ErrorCode.INVALID_CODE);
        assertThat(relink.run(111L, 900L, "K7QM2XPA").teacher().id()).isEqualTo(clara.id());
    }

    @Test
    void unlinkRecordsAuthorshipAndIsHarmlessWithoutALink() {
        unlink.run(
                ActorFixtures.administratorWeb(),
                new UnlinkTeacherTelegramUseCase.Command(clara.id(), CORRELATION));

        AuthorshipRecord record =
                c.recorder.ofOperation(Operation.TEACHER_UNLINK_TELEGRAM).getFirst();
        assertThat(record.targetId()).isEqualTo(clara.id());
    }

    @Test
    void unlinkOfUnknownTeacherIsNotFoundAndTeachersAreDenied() {
        assertThat(
                        failure(
                                        () ->
                                                unlink.run(
                                                        ActorFixtures.administratorWeb(),
                                                        new UnlinkTeacherTelegramUseCase.Command(
                                                                UUID.randomUUID(), CORRELATION)))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        c.link(clara, 111L, 900L);
        assertThat(
                        failure(
                                        () ->
                                                unlink.run(
                                                        ActorFixtures.teacherTelegram(clara.id()),
                                                        new UnlinkTeacherTelegramUseCase.Command(
                                                                clara.id(), CORRELATION)))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(c.links.all()).hasSize(1);
    }
}
