package br.com.felipeblaft.reports.saida.telegram;

import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.entrada.telegram.PublicacaoAcaoTelegramPorta;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram;
import br.com.felipeblaft.reports.entrada.telegram.canal.AcaoCanalMapeador;
import br.com.felipeblaft.reports.entrada.telegram.canal.AcaoCanalMapeador.AcaoMapeada;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Caminho do orquestrador: as notificações proativas viram ações v1 gravadas na outbox, de forma
 * idempotente por evento (FR-014), e o despachante as entrega ao n8n com retentativa. Nada é
 * enviado direto à API de bots enquanto este caminho está ativo (FR-019).
 */
@Component
@ConditionalOnProperty(
        prefix = "relatorios.canal-telegram",
        name = "entrega-proativa",
        havingValue = "ORQUESTRADOR")
public class PublicacaoOutboxTelegramAdaptador implements PublicacaoAcaoTelegramPorta {

    private final AcaoCanalMapeador mapeador;
    private final SaveChannelActionsUseCase gravar;

    public PublicacaoOutboxTelegramAdaptador(
            AcaoCanalMapeador mapeador, SaveChannelActionsUseCase gravar) {
        this.mapeador = mapeador;
        this.gravar = gravar;
    }

    @Override
    public void publicar(String eventoId, UUID professorId, List<SaidaTelegram> saidas) {
        if (saidas.isEmpty()) {
            return;
        }
        String loteId = "p:" + eventoId;
        long chat = chatDe(saidas.get(0));
        List<AcaoMapeada> acoes = mapeador.mapear(saidas, chat, pos -> loteId + ":" + pos);
        gravar.run(
                loteId,
                ChannelActionOrigin.PROATIVA,
                null,
                acoes.stream().map(a -> a.paraNovaAcao(professorId)).toList());
    }

    private static long chatDe(SaidaTelegram saida) {
        return switch (saida) {
            case SaidaTelegram.Mensagem m -> m.chatId();
            case SaidaTelegram.Edicao e -> e.chatId();
            case SaidaTelegram.Documento d -> d.chatId();
            case SaidaTelegram.MensagemEtapa e -> e.chatId();
            case SaidaTelegram.RespostaCallback r -> 0L;
        };
    }
}
