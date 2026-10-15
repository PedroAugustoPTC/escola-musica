package br.com.felipeblaft.reports.domain.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelegramConversationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration THIRTY = Duration.ofMinutes(30);

    @Test
    void emptyConversationStartsWithoutFlow() {
        TelegramConversation conversation = TelegramConversation.empty();
        assertThat(conversation.flow()).isEqualTo(ConversationFlow.NONE);
        assertThat(conversation.step()).isEqualTo(ConversationFlowStep.NONE);
        assertThat(conversation.data()).isEmpty();
        assertThat(conversation.pendingAudio()).isEmpty();
        assertThat(conversation.focusReport()).isEmpty();
        assertThat(conversation.selectedStudentId()).isNull();
        assertThat(conversation.presentedVersion()).isNull();
    }

    @Test
    void selectionAndFlow() {
        TelegramConversation conversation = TelegramConversation.empty();
        UUID student = UUID.randomUUID();
        conversation.selectStudent(student);
        conversation.startFlow(
                ConversationFlow.LESSON_REGISTRATION, ConversationFlowStep.CHOOSING_STUDENT);
        conversation.store("options", "a,b");
        assertThat(conversation.data("options")).contains("a,b");
        conversation.store("options", null);
        assertThat(conversation.data("options")).isEmpty();
        conversation.store("x", "1");
        conversation.defineStep(ConversationFlowStep.AWAITING_AUDIO);
        assertThat(conversation.step()).isEqualTo(ConversationFlowStep.AWAITING_AUDIO);
        conversation.startFlow(ConversationFlow.SEMESTER, ConversationFlowStep.CHOOSING_SEMESTER);
        assertThat(conversation.data()).isEmpty();
        assertThat(conversation.selectedStudentId()).isEqualTo(student);
        conversation.cleanSelection();
        assertThat(conversation.selectedStudentId()).isNull();
        assertThatThrownBy(() -> conversation.data().put("y", "2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pendingConfirmation() {
        TelegramConversation conversation = TelegramConversation.empty();
        conversation.askConfirmation("approve:2");
        assertThat(conversation.pendingConfirmation()).contains("approve:2");
        conversation.cleanConfirmation();
        assertThat(conversation.pendingConfirmation()).isEmpty();
    }

    @Test
    void newAudioReplacesAndExpiresInThirtyMinutes() {
        TelegramConversation conversation = TelegramConversation.empty();
        conversation.receiveAudio(new PendingAudio("f1", "u1", "audio/ogg", 100, T0));
        conversation.receiveAudio(
                new PendingAudio("f2", "u2", "audio/ogg", 200, T0.plusSeconds(60)));
        assertThat(conversation.pendingAudio()).map(PendingAudio::fileId).contains("f2");

        assertThat(conversation.discardAudioIfExpired(T0.plus(THIRTY), THIRTY)).isFalse();
        assertThat(conversation.discardAudioIfExpired(T0.plusSeconds(60).plus(THIRTY), THIRTY))
                .isTrue();
        assertThat(conversation.pendingAudio()).isEmpty();
        assertThat(conversation.discardAudioIfExpired(T0, THIRTY)).isFalse();

        conversation.receiveAudio(new PendingAudio("f3", null, null, 1, T0));
        conversation.discardAudio();
        assertThat(conversation.pendingAudio()).isEmpty();
    }

    @Test
    void focusAndPresentedVersion() {
        TelegramConversation conversation = TelegramConversation.empty();
        FocusReport focus = new FocusReport(ReportType.LESSON, UUID.randomUUID());
        conversation.focus(focus, 1);
        conversation.registerPresentedVersion(2);
        assertThat(conversation.focusReport()).contains(focus);
        assertThat(conversation.presentedVersion()).isEqualTo(2);
        conversation.cleanFocus();
        assertThat(conversation.focusReport()).isEmpty();
        assertThat(conversation.presentedVersion()).isNull();
    }

    @Test
    void cleanEverythingReturnsToHome() {
        TelegramConversation conversation =
                new TelegramConversation(
                        UUID.randomUUID(),
                        ConversationFlow.HISTORY,
                        ConversationFlowStep.CHOOSING_HISTORY_ITEM,
                        Map.of("page", "2"),
                        new PendingAudio("f", "u", "audio/ogg", 1, T0),
                        new FocusReport(ReportType.SEMESTER, UUID.randomUUID()),
                        3);
        conversation.cleanEverything();
        assertThat(conversation.flow()).isEqualTo(ConversationFlow.NONE);
        assertThat(conversation.data()).isEmpty();
        assertThat(conversation.pendingAudio()).isEmpty();
        assertThat(conversation.focusReport()).isEmpty();
        assertThat(conversation.selectedStudentId()).isNull();
    }
}
