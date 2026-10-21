package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.usecase.teacher.TeacherScenario;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Todo fim de vínculo: vínculo, cadastro em andamento e ações pendentes do chat saem juntos. */
class TelegramLinkTerminationServiceTest {

    private final TeacherScenario c = new TeacherScenario();
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
    private final Teacher bruno = c.teacher("Bruno Lima", "bruno@example.test", "BRUN2XPA");

    @Test
    void terminatingALinkRemovesItTheDraftAndThePendingActionsOfTheChat() {
        TelegramLink link = c.link(clara, 111L, 900L);
        c.draft(link);
        c.pendingAction("a1", 900L);
        c.pendingAction("a2", 900L);

        c.termination.terminate(link);

        assertThat(c.links.searchByTeacherId(clara.id())).isEmpty();
        assertThat(c.drafts.searchByLinkId(link.id())).isEmpty();
        assertThat(c.actions.stored("a1").state()).isEqualTo(ChannelActionState.DISCARDED);
        assertThat(c.actions.stored("a2").state()).isEqualTo(ChannelActionState.DISCARDED);
    }

    @Test
    void doesNotTouchOtherLinksDraftsOrChats() {
        TelegramLink claraLink = c.link(clara, 111L, 900L);
        TelegramLink brunoLink = c.link(bruno, 222L, 901L);
        c.draft(brunoLink);
        c.pendingAction("other", 901L);

        c.termination.terminate(claraLink);

        assertThat(c.links.searchByTeacherId(bruno.id())).isPresent();
        assertThat(c.drafts.searchByLinkId(brunoLink.id())).isPresent();
        assertThat(c.actions.stored("other").state()).isEqualTo(ChannelActionState.PENDING);
    }

    @Test
    void worksWithoutDraftOrPendingActions() {
        TelegramLink link = c.link(clara, 111L, 900L);

        c.termination.terminate(link);

        assertThat(c.links.all()).isEmpty();
        assertThat(c.drafts.size()).isZero();
    }

    @Test
    void terminatingAnAlreadyRemovedLinkIsHarmless() {
        TelegramLink link = c.link(clara, 111L, 900L);
        c.termination.terminate(link);

        c.termination.terminate(link);

        assertThat(c.links.removals()).isEqualTo(1);
    }

    @Test
    void terminatingByTeacherEndsHisLinkAndReportsIt() {
        TelegramLink link = c.link(clara, 111L, 900L);
        c.draft(link);
        c.pendingAction("a1", 900L);

        assertThat(c.termination.terminateOfTeacher(clara.id())).isTrue();

        assertThat(c.links.all()).isEmpty();
        assertThat(c.drafts.size()).isZero();
        assertThat(c.actions.stored("a1").state()).isEqualTo(ChannelActionState.DISCARDED);
    }

    @Test
    void terminatingByTeacherWithoutLinkReportsFalseAndChangesNothing() {
        c.link(bruno, 222L, 901L);

        assertThat(c.termination.terminateOfTeacher(clara.id())).isFalse();
        assertThat(c.termination.terminateOfTeacher(UUID.randomUUID())).isFalse();

        assertThat(c.links.all()).hasSize(1);
    }

    @Test
    void tellsWhetherTheTeacherHasALink() {
        c.link(clara, 111L, 900L);

        assertThat(c.termination.hasLink(clara.id())).isTrue();
        assertThat(c.termination.hasLink(bruno.id())).isFalse();
    }
}
