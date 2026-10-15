package br.com.felipeblaft.reports.domain.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelegramLinkTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");

    @Test
    void newLinkWithEmptyConversation() {
        UUID teacher = UUID.randomUUID();
        TelegramLink link = TelegramLink.create(teacher, 111L, 222L, T0);
        assertThat(link.teacherId()).isEqualTo(teacher);
        assertThat(link.telegramUserId()).isEqualTo(111L);
        assertThat(link.chatId()).isEqualTo(222L);
        assertThat(link.linkedAt()).isEqualTo(T0);
        assertThat(link.conversation().flow()).isEqualTo(ConversationFlow.NONE);
        assertThat(link.version()).isZero();
        assertThat(link.id()).isNotNull();

        link.updateChat(333L, T0.plusSeconds(1));
        link.play(T0.plusSeconds(2));
        assertThat(link.chatId()).isEqualTo(333L);
        assertThat(link.updatedAt()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    void nullConversationBecomesEmpty() {
        TelegramLink link =
                new TelegramLink(UUID.randomUUID(), UUID.randomUUID(), 1, 1, T0, null, T0, 4);
        assertThat(link.conversation()).isNotNull();
        assertThat(link.version()).isEqualTo(4);
        link.registerPersistedVersion(5);
        assertThat(link.version()).isEqualTo(5);
    }

    @Test
    void pendingAudioRequiresReference() {
        assertThatThrownBy(() -> new PendingAudio(null, null, null, 0, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FocusReport(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }
}
