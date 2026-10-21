package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryChannelActionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");
    private static final long CHAT = 555L;
    private static final long OTHER_CHAT = 777L;

    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));
    private final InMemoryChannelActionRepository actions = new InMemoryChannelActionRepository();

    private ChannelAction proactive(String id, long chat) {
        return ChannelAction.create(
                id,
                "batch-" + id,
                ChannelActionOrigin.PROACTIVE,
                null,
                chat,
                null,
                null,
                ChannelActionType.MESSAGE,
                "{\"text\":\"olá\"}",
                null,
                NOW,
                policy);
    }

    private ChannelAction reply(String id, long chat) {
        return ChannelAction.create(
                id,
                "batch-" + id,
                ChannelActionOrigin.UPDATE_REPLY,
                42L,
                chat,
                null,
                null,
                ChannelActionType.MESSAGE,
                "{\"text\":\"olá\"}",
                null,
                NOW,
                policy);
    }

    @Test
    void discardPendingByChatDiscardsOnlyPendingActionsOfThatChat() {
        actions.saveBatch(
                List.of(proactive("a1", CHAT), proactive("a2", CHAT), proactive("b1", OTHER_CHAT)));

        int discarded = actions.discardPendingByChat(CHAT, NOW.plusSeconds(5));

        assertThat(discarded).isEqualTo(2);
        assertThat(actions.stored("a1").state()).isEqualTo(ChannelActionState.DISCARDED);
        assertThat(actions.stored("a1").reasonCode()).isEqualTo(ChannelAction.LINK_UNDONE_CODE);
        assertThat(actions.stored("a2").state()).isEqualTo(ChannelActionState.DISCARDED);
        assertThat(actions.stored("b1").state()).isEqualTo(ChannelActionState.PENDING);
    }

    @Test
    void discardPendingByChatLeavesDeliveredAndFinalizedActionsUntouched() {
        ChannelAction delivered = reply("r1", CHAT);
        ChannelAction executed = reply("r2", CHAT);
        executed.confirmExecuted(NOW);
        actions.saveBatch(List.of(delivered, executed, proactive("p1", CHAT)));

        int discarded = actions.discardPendingByChat(CHAT, NOW.plusSeconds(5));

        assertThat(discarded).isEqualTo(1);
        assertThat(actions.stored("r1").state()).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(actions.stored("r2").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void discardedActionsAreNotReservedForDelivery() {
        actions.saveBatch(List.of(proactive("a1", CHAT)));
        actions.discardPendingByChat(CHAT, NOW);

        assertThat(actions.reserveNextDueBatch(NOW, Duration.ofMinutes(5), NOW.plusSeconds(60)))
                .isEmpty();
    }

    @Test
    void discardAdvancesTheVersionOfEachDiscardedAction() {
        actions.saveBatch(List.of(proactive("a1", CHAT)));
        int before = actions.stored("a1").version();

        actions.discardPendingByChat(CHAT, NOW);

        assertThat(actions.stored("a1").version()).isEqualTo(before + 1);
    }

    @Test
    void chatWithoutPendingActionsDiscardsZero() {
        actions.saveBatch(List.of(proactive("a1", OTHER_CHAT)));

        assertThat(actions.discardPendingByChat(CHAT, NOW)).isZero();
        assertThat(actions.stored("a1").state()).isEqualTo(ChannelActionState.PENDING);
    }
}
