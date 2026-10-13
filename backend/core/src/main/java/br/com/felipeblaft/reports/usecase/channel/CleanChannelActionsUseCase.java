package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Retenção da outbox (R-11): ação entregue e nunca confirmada expira no prazo; o conteúdo das ações
 * finalizadas some depois de {@code retencaoPayload}; o registro (sem conteúdo) some depois de
 * {@code retencaoRegistro}; documentos vencidos são removidos.
 */
public class CleanChannelActionsUseCase {

    public record Result(
            int expiredActions,
            int resetPayloads,
            int removedRegistrations,
            int removedDocuments) {}

    private final ChannelActionRepository actions;
    private final ChannelDocumentRepository documents;
    private final ClockPort clock;
    private final Duration payloadRetention;
    private final Duration recordRetention;

    public CleanChannelActionsUseCase(
            ChannelActionRepository actions,
            ChannelDocumentRepository documents,
            ClockPort clock,
            Duration payloadRetention,
            Duration recordRetention) {
        this.actions = Objects.requireNonNull(actions);
        this.documents = Objects.requireNonNull(documents);
        this.clock = Objects.requireNonNull(clock);
        this.payloadRetention = Objects.requireNonNull(payloadRetention);
        this.recordRetention = Objects.requireNonNull(recordRetention);
    }

    public Result run() {
        Instant now = clock.now();
        int expired = actions.expireOverdueDelivered(now);
        ChannelActionRepository.Purge purge =
                actions.purge(now.minus(payloadRetention), now.minus(recordRetention));
        int removed = documents.purgeExpired(now);
        return new Result(expired, purge.resetPayloads(), purge.removedRegistrations(), removed);
    }
}
