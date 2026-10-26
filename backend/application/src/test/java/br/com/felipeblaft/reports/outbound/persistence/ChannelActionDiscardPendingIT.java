package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Fim do vínculo descarta só as ações PENDING do chat (feature 005, T083). */
class ChannelActionDiscardPendingIT extends IntegrationBase {

    private static final Instant T0 = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant DISCARD_AT = T0.plusSeconds(90);
    private static final long CHAT = 4_001L;
    private static final long OTHER_CHAT = 4_002L;

    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    @Autowired ChannelActionRepository actions;

    private ChannelAction action(String id, long chatId) {
        return ChannelAction.create(
                id,
                "batch:" + id,
                ChannelActionOrigin.PROACTIVE,
                null,
                chatId,
                UUID.randomUUID(),
                null,
                ChannelActionType.MESSAGE,
                "{\"text\":\"" + id + "\"}",
                null,
                T0,
                policy);
    }

    private void deliver(String id) {
        ChannelAction action = actions.searchById(id).orElseThrow();
        action.markDelivered(T0.plusSeconds(5));
        assertThat(actions.updateIfVersion(action, action.version())).isTrue();
    }

    private void execute(String id) {
        deliver(id);
        ChannelAction action = actions.searchById(id).orElseThrow();
        action.confirmExecuted(T0.plusSeconds(10));
        assertThat(actions.updateIfVersion(action, action.version())).isTrue();
    }

    @Test
    void discardsOnlyPendingOfTheChat() {
        actions.saveBatch(
                List.of(
                        action("pending-1", CHAT),
                        action("pending-2", CHAT),
                        action("delivered", CHAT),
                        action("executed", CHAT),
                        action("other-chat", OTHER_CHAT)));
        deliver("delivered");
        execute("executed");

        int discarded = actions.discardPendingByChat(CHAT, DISCARD_AT);

        assertThat(discarded).isEqualTo(2);
        for (String id : List.of("pending-1", "pending-2")) {
            ChannelAction read = actions.searchById(id).orElseThrow();
            assertThat(read.state()).isEqualTo(ChannelActionState.DISCARDED);
            assertThat(read.reasonCode()).isEqualTo(ChannelAction.LINK_UNDONE_CODE);
            assertThat(read.finalizedAt()).isEqualTo(DISCARD_AT);
            assertThat(read.nextAttemptAt()).isNull();
        }
        assertThat(actions.searchById("delivered").orElseThrow().state())
                .isEqualTo(ChannelActionState.DELIVERED);
        assertThat(actions.searchById("executed").orElseThrow().state())
                .isEqualTo(ChannelActionState.EXECUTED);
        ChannelAction other = actions.searchById("other-chat").orElseThrow();
        assertThat(other.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(other.reasonCode()).isNull();
    }

    @Test
    void discardedActionIsNeverReservedForDelivery() {
        actions.saveBatch(List.of(action("pending-1", CHAT), action("other-chat", OTHER_CHAT)));

        actions.discardPendingByChat(CHAT, DISCARD_AT);

        List<ChannelAction> reserved =
                actions.reserveNextDueBatch(
                        T0.plusSeconds(120), Duration.ofSeconds(60), T0.plusSeconds(180));
        assertThat(reserved).extracting(ChannelAction::id).containsExactly("other-chat");
    }

    @Test
    void repeatedDiscardAndChatWithoutActionsReturnZero() {
        actions.saveBatch(List.of(action("pending-1", CHAT)));

        assertThat(actions.discardPendingByChat(CHAT, DISCARD_AT)).isEqualTo(1);
        assertThat(actions.discardPendingByChat(CHAT, DISCARD_AT.plusSeconds(1))).isZero();
        assertThat(actions.discardPendingByChat(9_999L, DISCARD_AT)).isZero();
        assertThat(actions.searchById("pending-1").orElseThrow().finalizedAt())
                .isEqualTo(DISCARD_AT);
    }

    @Test
    void discardAdvancesTheVersionSoLateConfirmationLoses() {
        actions.saveBatch(List.of(action("pending-1", CHAT)));
        ChannelAction stale = actions.searchById("pending-1").orElseThrow();

        actions.discardPendingByChat(CHAT, DISCARD_AT);

        stale.markDelivered(T0.plusSeconds(100));
        assertThat(actions.updateIfVersion(stale, 0)).isFalse();
        assertThat(actions.searchById("pending-1").orElseThrow().state())
                .isEqualTo(ChannelActionState.DISCARDED);
    }
}
