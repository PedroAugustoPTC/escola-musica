package br.com.felipeblaft.reports.usecase.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.doubles.AccessScenario;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Vincular, identificar, atualizar a conversa e encerrar o vínculo do Telegram. */
class LinkTelegramUseCaseTest {

    private final AccessScenario c = new AccessScenario();
    private final LinkTelegramUseCase linkUseCase =
            new LinkTelegramUseCase(c.authentication, c.renewal, c.links, c.clock);
    private final IdentifyTelegramTeacherUseCase identify =
            new IdentifyTelegramTeacherUseCase(c.links, c.teachers, c.renewal);
    private final EndTelegramLinkUseCase end = new EndTelegramLinkUseCase(c.links);
    private final UpdateTelegramConversationUseCase update =
            new UpdateTelegramConversationUseCase(c.links, c.clock);
    private final Teacher clara = c.teacher("Clara Souza", "clara@exemplo.test", "K7QM2XPA");

    private static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    @Test
    void linksWithSameCodeWithoutConsuming() {
        QueryTelegramLinkUseCase query = new QueryTelegramLinkUseCase(c.links);
        assertThat(query.run(clara.id())).isFalse();
        LinkTelegramUseCase.Result result = linkUseCase.run(111L, 999L, "k7qm2xpa");
        assertThat(query.run(clara.id())).isTrue();

        assertThat(result.teacher().id()).isEqualTo(clara.id());
        assertThat(result.link().chatId()).isEqualTo(999L);
        assertThat(c.links.searchByTelegramUserId(111L)).isPresent();
        assertThat(clara.accessCodeHash()).isEqualTo("codigo:K7QM2XPA");
    }

    @Test
    void accountAlreadyLinkedOrTeacherAlreadyLinked() {
        Teacher bruno = c.teacher("Bruno Lima", "bruno@exemplo.test", "BRUN2XPA");
        linkUseCase.run(111L, 1L, "K7QM2XPA");
        failWith(() -> linkUseCase.run(111L, 1L, "BRUN2XPA"), ErrorCode.TELEGRAM_JA_VINCULADO);
        failWith(() -> linkUseCase.run(222L, 2L, "K7QM2XPA"), ErrorCode.PROFESSOR_JA_VINCULADO);
        assertThat(c.links.searchByTeacherId(bruno.id())).isEmpty();
    }

    @Test
    void checksLockBeforeCodeAndExpiredCode() {
        for (int i = 0; i < 5; i++) {
            failWith(() -> linkUseCase.run(111L, 1L, "ERRADO22"), ErrorCode.CODIGO_INVALIDO);
        }
        failWith(() -> linkUseCase.run(111L, 1L, "K7QM2XPA"), ErrorCode.BLOQUEADO_TEMPORARIAMENTE);
        c.clock.advance(Duration.ofDays(8));
        failWith(() -> linkUseCase.run(111L, 1L, "K7QM2XPA"), ErrorCode.CODIGO_EXPIRADO);
    }

    @Test
    void inactiveTeacherCannotLink() {
        clara.updateRegistry(clara.name(), false, c.clock.now());
        failWith(() -> linkUseCase.run(111L, 1L, "K7QM2XPA"), ErrorCode.CODIGO_INVALIDO);
    }

    @Test
    void identifiesAndRenewsCode() {
        linkUseCase.run(111L, 1L, "K7QM2XPA");
        c.clock.advance(Duration.ofDays(6));

        var identification = identify.run(111L).orElseThrow();

        assertThat(identification.teacher().id()).isEqualTo(clara.id());
        assertThat(identification.link().telegramUserId()).isEqualTo(111L);
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().accessCodeExpiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(identify.run(222L)).isEmpty();
    }

    @Test
    void deactivatedTeacherLosesLink() {
        linkUseCase.run(111L, 1L, "K7QM2XPA");
        clara.updateRegistry(clara.name(), false, c.clock.now());
        assertThat(identify.run(111L)).isEmpty();
        assertThat(c.links.all()).isEmpty();
    }

    @Test
    void updatesConversationAndEndsLink() {
        TelegramLink link = linkUseCase.run(111L, 1L, "K7QM2XPA").link();
        link.conversation().startFlow(ConversationFlow.HISTORICO, link.conversation().step());
        c.clock.advance(Duration.ofMinutes(1));
        update.run(link);
        assertThat(c.links.searchByTelegramUserId(111L).orElseThrow().updatedAt())
                .isEqualTo(c.clock.now());

        end.run(111L);
        end.run(222L);
        assertThat(c.links.all()).isEmpty();
        assertThat(identify.run(111L)).isEmpty();
    }
}
