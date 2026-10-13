package br.com.felipeblaft.reports.saida.persistencia;

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
final class VinculoTelegramMapeador {

    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private VinculoTelegramMapeador() {}

    static TelegramLink paraDominio(VinculoTelegramEntidade e) {
        PendingAudio audio =
                e.audioPendenteFileId == null
                        ? null
                        : new PendingAudio(
                                e.audioPendenteFileId,
                                e.audioPendenteFileUniqueId,
                                e.audioPendenteMime,
                                e.audioPendenteTamanho == null ? 0 : e.audioPendenteTamanho,
                                e.audioPendenteRecebidoEm);
        FocusReport foco =
                e.relatorioFocoId == null
                        ? null
                        : new FocusReport(
                                ReportType.valueOf(e.relatorioFocoTipo), e.relatorioFocoId);
        TelegramConversation conversa =
                new TelegramConversation(
                        e.alunoSelecionadoId,
                        ConversationFlow.valueOf(e.fluxo),
                        ConversationFlowStep.valueOf(e.etapaFluxo),
                        JsonPersistencia.ler(e.dadosFluxo, MAPA),
                        audio,
                        foco,
                        e.versaoApresentada);
        return new TelegramLink(
                e.id,
                e.professorId,
                e.telegramUsuarioId,
                e.chatId,
                e.vinculadoEm,
                conversa,
                e.atualizadoEm,
                e.bloqueioOtimista);
    }

    static VinculoTelegramEntidade paraEntidade(TelegramLink v) {
        TelegramConversation c = v.conversation();
        VinculoTelegramEntidade e = new VinculoTelegramEntidade();
        e.id = v.id();
        e.professorId = v.teacherId();
        e.telegramUsuarioId = v.telegramUserId();
        e.chatId = v.chatId();
        e.vinculadoEm = v.linkedAt();
        e.alunoSelecionadoId = c.selectedStudentId();
        e.fluxo = c.flow().name();
        e.etapaFluxo = c.step().name();
        e.dadosFluxo = JsonPersistencia.escrever(c.data());
        c.pendingAudio()
                .ifPresent(
                        a -> {
                            e.audioPendenteFileId = a.fileId();
                            e.audioPendenteFileUniqueId = a.fileUniqueId();
                            e.audioPendenteMime = a.mime();
                            e.audioPendenteTamanho = a.size();
                            e.audioPendenteRecebidoEm = a.receivedAt();
                        });
        c.focusReport()
                .ifPresent(
                        f -> {
                            e.relatorioFocoTipo = f.type().name();
                            e.relatorioFocoId = f.id();
                        });
        e.versaoApresentada = c.presentedVersion();
        e.atualizadoEm = v.updatedAt();
        e.bloqueioOtimista = v.version();
        return e;
    }
}
