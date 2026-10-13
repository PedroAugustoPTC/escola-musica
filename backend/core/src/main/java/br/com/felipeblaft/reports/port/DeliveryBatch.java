package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lote de ações entregue inteiro ao orquestrador. O {@code payload} é opaco para o núcleo; {@code
 * editarMensagemId} só vem preenchido nas mensagens de etapa cujo trabalho já tem a mensagem
 * conhecida.
 */
public record DeliveryBatch(
        String batchId,
        ChannelActionOrigin origin,
        Instant createdAt,
        int attempt,
        List<Action> actions) {

    public record Action(
            String actionId,
            ChannelActionType type,
            String payload,
            UUID jobId,
            Long editMessageId) {}
}
