package br.com.felipeblaft.reports.usecase.access;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.usecase.teacher.TeacherScenario;
import org.junit.jupiter.api.Test;

/** "Sair" encerra o vínculo pelo mesmo caminho de todo fim de vínculo. */
class EndTelegramLinkUseCaseTest {

    private final TeacherScenario c = new TeacherScenario();
    private final EndTelegramLinkUseCase end = new EndTelegramLinkUseCase(c.links, c.termination);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
    private final Teacher bruno = c.teacher("Bruno Lima", "bruno@example.test", "BRUN2XPA");

    @Test
    void endsLinkDiscardsDraftAndPendingActionsOfTheChat() {
        TelegramLink link = c.link(clara, 111L, 900L);
        c.draft(link);
        c.pendingAction("a1", 900L);
        c.pendingAction("a2", 900L);

        end.run(111L);

        assertThat(c.links.searchByTelegramUserId(111L)).isEmpty();
        assertThat(c.drafts.searchByLinkId(link.id())).isEmpty();
        assertThat(c.actions.stored("a1").state()).isEqualTo(ChannelActionState.DISCARDED);
        assertThat(c.actions.stored("a2").state()).isEqualTo(ChannelActionState.DISCARDED);
    }

    @Test
    void leavesOtherAccountsUntouched() {
        c.link(clara, 111L, 900L);
        TelegramLink other = c.link(bruno, 222L, 901L);
        c.draft(other);
        c.pendingAction("b1", 901L);

        end.run(111L);

        assertThat(c.links.searchByTelegramUserId(222L)).isPresent();
        assertThat(c.drafts.searchByLinkId(other.id())).isPresent();
        assertThat(c.actions.stored("b1").state()).isEqualTo(ChannelActionState.PENDING);
    }

    @Test
    void unknownAccountIsIgnored() {
        c.link(clara, 111L, 900L);

        end.run(999L);

        assertThat(c.links.all()).hasSize(1);
    }
}
