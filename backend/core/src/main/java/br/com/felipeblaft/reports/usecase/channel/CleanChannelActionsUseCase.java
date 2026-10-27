package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Retenção da outbox (R-11): ação entregue e nunca confirmada expira no prazo; o conteúdo das ações
 * finalizadas some depois de {@code retencaoPayload}; o registro (sem conteúdo) some depois de
 * {@code retencaoRegistro}; documentos vencidos são removidos. A mensagem a apagar (o CPF no chat)
 * que expira sem confirmação gera o aviso alternativo, uma só vez (FR-036).
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
    private final RetryPolicy policy;
    private final Duration payloadRetention;
    private final Duration recordRetention;

    public CleanChannelActionsUseCase(
            ChannelActionRepository actions,
            ChannelDocumentRepository documents,
            ClockPort clock,
            RetryPolicy policy,
            Duration payloadRetention,
            Duration recordRetention) {
        this.actions = Objects.requireNonNull(actions);
        this.documents = Objects.requireNonNull(documents);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
        this.payloadRetention = Objects.requireNonNull(payloadRetention);
        this.recordRetention = Objects.requireNonNull(recordRetention);
    }

    public Result run() {
        Instant now = clock.now();
        int expired = expireDeletions(now);
        expired += actions.expireOverdueDelivered(now);
        ChannelActionRepository.Purge purge =
                actions.purge(now.minus(payloadRetention), now.minus(recordRetention));
        int removed = documents.purgeExpired(now);
        return new Result(expired, purge.resetPayloads(), purge.removedRegistrations(), removed);
    }

    /**
     * Mensagens a apagar entregues e nunca confirmadas expiram uma a uma, para que o aviso
     * alternativo seja gerado; as demais ações expiram em lote.
     */
    private int expireDeletions(Instant now) {
        int expired = 0;
        for (ChannelAction action :
                actions.searchOverdueDelivered(now, ChannelActionType.DELETE_MESSAGE)) {
            int readVersion = action.version();
            if (action.expireWithoutConfirmation(now)
                    && actions.updateIfVersion(action, readVersion)) {
                expired++;
                if (action.compensatesPermanentFailure()) {
                    actions.saveBatch(List.of(action.createCompensation(now, policy)));
                }
            }
        }
        return expired;
    }
}
