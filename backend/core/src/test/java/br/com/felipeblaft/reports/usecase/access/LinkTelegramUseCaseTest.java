package br.com.felipeblaft.reports.usecase.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.usecase.teacher.TeacherScenario;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Vincular, identificar, atualizar a conversa e encerrar o vínculo do Telegram. */
class LinkTelegramUseCaseTest {

    private final TeacherScenario c = new TeacherScenario();
    private final LinkTelegramUseCase linkUseCase =
            new LinkTelegramUseCase(c.authentication, c.renewal, c.links, c.audit, c.clock);
    private final IdentifyTelegramTeacherUseCase identify =
            new IdentifyTelegramTeacherUseCase(c.links, c.teachers, c.renewal, c.termination);
    private final EndTelegramLinkUseCase end = new EndTelegramLinkUseCase(c.links, c.termination);
    private final UpdateTelegramConversationUseCase update =
            new UpdateTelegramConversationUseCase(c.links, c.clock);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");

    private static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    private LinkTelegramUseCase.Result link(long user, long chat, String code) {
        return linkUseCase.run(user, chat, code, "98765");
    }

    @Test
    void linksWithSameCodeWithoutConsuming() {
        QueryTelegramLinkUseCase query = new QueryTelegramLinkUseCase(c.links);
        assertThat(query.run(clara.id())).isFalse();
        LinkTelegramUseCase.Result result = link(111L, 999L, "k7qm2xpa");
        assertThat(query.run(clara.id())).isTrue();

        assertThat(result.teacher().id()).isEqualTo(clara.id());
        assertThat(result.link().chatId()).isEqualTo(999L);
        assertThat(c.links.searchByTelegramUserId(111L)).isPresent();
        assertThat(clara.accessCodeHash()).isEqualTo("code:K7QM2XPA");
    }

    @Test
    void overloadWithoutCorrelationStillLinksAndRecords() {
        assertThat(linkUseCase.run(111L, 1L, "K7QM2XPA").teacher().id()).isEqualTo(clara.id());
        assertThat(c.recorder.ofOperation("TEACHER_LINK_TELEGRAM")).hasSize(1);
    }

    @Test
    void accountAlreadyLinkedOrTeacherAlreadyLinked() {
        Teacher bruno = c.teacher("Bruno Lima", "bruno@example.test", "BRUN2XPA");
        link(111L, 1L, "K7QM2XPA");
        failWith(() -> link(111L, 1L, "BRUN2XPA"), ErrorCode.TELEGRAM_ALREADY_LINKED);
        failWith(() -> link(222L, 2L, "K7QM2XPA"), ErrorCode.TEACHER_ALREADY_LINKED);
        assertThat(c.links.searchByTeacherId(bruno.id())).isEmpty();
        assertThat(c.links.searchByTelegramUserId(111L).orElseThrow().teacherId())
                .isEqualTo(clara.id());
        assertThat(c.links.removals()).isZero();
    }

    @Test
    void checksLockBeforeCodeAndExpiredCode() {
        for (int i = 0; i < 5; i++) {
            failWith(() -> link(111L, 1L, "WRONG222"), ErrorCode.INVALID_CODE);
        }
        failWith(() -> link(111L, 1L, "K7QM2XPA"), ErrorCode.TEMPORARILY_BLOCKED);
        c.clock.advance(Duration.ofDays(8));
        failWith(() -> link(111L, 1L, "K7QM2XPA"), ErrorCode.INVALID_CODE);
    }

    @Test
    void inactiveTeacherCannotLink() {
        clara.inactivate(c.clock.now());
        failWith(() -> link(111L, 1L, "K7QM2XPA"), ErrorCode.INVALID_CODE);
    }

    @Test
    void identifiesAndRenewsCode() {
        link(111L, 1L, "K7QM2XPA");
        c.clock.advance(Duration.ofDays(6));

        var identification = identify.run(111L).orElseThrow();

        assertThat(identification.teacher().id()).isEqualTo(clara.id());
        assertThat(identification.link().telegramUserId()).isEqualTo(111L);
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().accessCodeExpiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(identify.run(222L)).isEmpty();
    }

    @Test
    void inactiveTeacherLosesTheLinkThroughTheTerminationService() {
        TelegramLink telegramLink = link(111L, 1L, "K7QM2XPA").link();
        c.draft(telegramLink);
        c.pendingAction("a1", 1L);
        clara.updateRegistry(clara.name(), false, c.clock.now());

        assertThat(identify.run(111L)).isEmpty();

        assertThat(c.links.all()).isEmpty();
        assertThat(c.drafts.size()).isZero();
        assertThat(c.actions.stored("a1").state().name()).isEqualTo("DISCARDED");
    }

    @Test
    void updatesConversationAndEndsLink() {
        TelegramLink telegramLink = link(111L, 1L, "K7QM2XPA").link();
        telegramLink
                .conversation()
                .startFlow(ConversationFlow.HISTORY, telegramLink.conversation().step());
        c.clock.advance(Duration.ofMinutes(1));
        update.run(telegramLink);
        assertThat(c.links.searchByTelegramUserId(111L).orElseThrow().updatedAt())
                .isEqualTo(c.clock.now());

        end.run(111L);
        end.run(222L);
        assertThat(c.links.all()).isEmpty();
        assertThat(identify.run(111L)).isEmpty();
    }

    // ---- autoria (FR-040)

    @Test
    void successRecordsTeacherLinkTelegramWithTelegramOrigin() {
        link(111L, 999L, "K7QM2XPA");

        AuthorshipRecord record = c.recorder.inTransaction().getFirst();
        assertThat(record.operation()).isEqualTo("TEACHER_LINK_TELEGRAM");
        assertThat(record.authorRole()).isEqualTo(Role.TEACHER);
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.originHash()).isEqualTo(c.protection.originHash(Channel.TELEGRAM, "111"));
        assertThat(record.correlationId()).isEqualTo("98765");
        assertThat(record.toString()).doesNotContain("K7QM2XPA");
    }

    @Test
    void unknownCodeRecordsFailureWithNullAuthor() {
        failWith(() -> link(111L, 1L, "WRONG222"), ErrorCode.INVALID_CODE);

        AuthorshipRecord record = c.recorder.independent().getFirst();
        assertThat(record.operation()).isEqualTo("TEACHER_LINK_TELEGRAM");
        assertThat(record.authorId()).isNull();
        assertThat(record.authorRole()).isNull();
        assertThat(record.errorCode()).isEqualTo("UNKNOWN_CODE");
        assertThat(record.toString()).doesNotContain("WRONG222");
    }

    @Test
    void refusalOfAnAlreadyLinkedAccountRecordsTheTeacherAsAuthor() {
        link(111L, 1L, "K7QM2XPA");
        failWith(() -> link(222L, 2L, "K7QM2XPA"), ErrorCode.TEACHER_ALREADY_LINKED);

        AuthorshipRecord record = c.recorder.independent().getFirst();
        assertThat(record.operation()).isEqualTo("TEACHER_LINK_TELEGRAM");
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(record.errorCode()).isEqualTo("TEACHER_ALREADY_LINKED");
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.authorRole()).isEqualTo(Role.TEACHER);
    }

    @Test
    void blockRecordsAttemptBlocked() {
        for (int i = 0; i < 5; i++) {
            failWith(() -> link(111L, 1L, "WRONG222"), ErrorCode.INVALID_CODE);
        }
        failWith(() -> link(111L, 1L, "K7QM2XPA"), ErrorCode.TEMPORARILY_BLOCKED);

        AuthorshipRecord blocked = c.recorder.ofOperation("ATTEMPT_BLOCKED").getFirst();
        assertThat(blocked.result()).isEqualTo(AuthorshipResult.BLOCKED);
        assertThat(blocked.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(blocked.authorId()).isNull();
        assertThat(blocked.originHash())
                .isEqualTo(c.protection.originHash(Channel.TELEGRAM, "111"));
    }

    @Test
    void failureToRecordAuthorshipKeepsTheLinkUndone() {
        c.recorder.failNextRecordWith(new IllegalStateException("falha ao gravar"));

        assertThatThrownBy(() -> link(111L, 1L, "K7QM2XPA"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(c.links.all()).isEmpty();
    }
}
