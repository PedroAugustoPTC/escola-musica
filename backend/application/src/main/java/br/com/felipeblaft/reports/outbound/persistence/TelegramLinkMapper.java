package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.PendingAudio;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

/** Conversão do vínculo e da conversa (colunas planas + {@code dados_fluxo} em jsonb). */
final class TelegramLinkMapper {

    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {};

    private TelegramLinkMapper() {}

    static TelegramLink toDomain(TelegramLinkEntity e) {
        PendingAudio audio =
                e.pendingAudioFileId == null
                        ? null
                        : new PendingAudio(
                                e.pendingAudioFileId,
                                e.pendingAudioFileUniqueId,
                                e.pendingAudioMime,
                                e.pendingAudioSize == null ? 0 : e.pendingAudioSize,
                                e.pendingAudioReceivedAt);
        FocusReport focus =
                e.focusedReportId == null
                        ? null
                        : new FocusReport(
                                ReportType.valueOf(e.focusedReportType), e.focusedReportId);
        TelegramConversation conversation =
                new TelegramConversation(
                        e.selectedStudentId,
                        ConversationFlow.valueOf(e.flow),
                        ConversationFlowStep.valueOf(e.flowStep),
                        JsonPersistence.read(e.flowData, MAP),
                        audio,
                        focus,
                        e.presentedVersion);
        return new TelegramLink(
                e.id,
                e.teacherId,
                e.telegramUserId,
                e.chatId,
                e.linkedAt,
                conversation,
                e.updatedAt,
                e.lockVersion);
    }

    static TelegramLinkEntity toEntity(TelegramLink v) {
        TelegramConversation c = v.conversation();
        TelegramLinkEntity e = new TelegramLinkEntity();
        e.id = v.id();
        e.teacherId = v.teacherId();
        e.telegramUserId = v.telegramUserId();
        e.chatId = v.chatId();
        e.linkedAt = v.linkedAt();
        e.selectedStudentId = c.selectedStudentId();
        e.flow = c.flow().name();
        e.flowStep = c.step().name();
        e.flowData = JsonPersistence.write(c.data());
        c.pendingAudio()
                .ifPresent(
                        a -> {
                            e.pendingAudioFileId = a.fileId();
                            e.pendingAudioFileUniqueId = a.fileUniqueId();
                            e.pendingAudioMime = a.mime();
                            e.pendingAudioSize = a.size();
                            e.pendingAudioReceivedAt = a.receivedAt();
                        });
        c.focusReport()
                .ifPresent(
                        f -> {
                            e.focusedReportType = f.type().name();
                            e.focusedReportId = f.id();
                        });
        e.presentedVersion = c.presentedVersion();
        e.updatedAt = v.updatedAt();
        e.lockVersion = v.version();
        return e;
    }
}
