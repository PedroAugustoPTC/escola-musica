package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Grava as ações de um evento na outbox, de forma idempotente por {@code acaoId} (FR-014): o mesmo
 * evento processado duas vezes não cria ação nova. Respostas a update nascem {@code ENTREGUE}
 * (seguem na resposta HTTP); proativas e compensações nascem {@code PENDENTE}.
 */
public class SaveChannelActionsUseCase {

    /**
     * Ação já mapeada pelo adaptador de entrada; {@code payload} e {@code compensacao} são opacos.
     */
    public record NewAction(
            String id,
            ChannelActionType type,
            long chatId,
            UUID teacherId,
            UUID jobId,
            String payload,
            String compensation) {}

    private final ChannelActionRepository actions;
    private final ClockPort clock;
    private final RetryPolicy policy;

    public SaveChannelActionsUseCase(
            ChannelActionRepository actions, ClockPort clock, RetryPolicy policy) {
        this.actions = Objects.requireNonNull(actions);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
    }

    /**
     * @param loteId evento de origem ({@code u<update_id>}, {@code p:<evento>} ou {@code
     *     c:<acaoId>})
     * @param updateId obrigatório em {@link ChannelActionOrigin#UPDATE_REPLY}
     * @return quantas ações foram de fato gravadas (as já existentes não contam)
     */
    public int run(
            String batchId, ChannelActionOrigin origin, Long updateId, List<NewAction> newActions) {
        Instant now = clock.now();
        List<ChannelAction> batch =
                newActions.stream()
                        .map(
                                created ->
                                        ChannelAction.create(
                                                created.id(),
                                                batchId,
                                                origin,
                                                updateId,
                                                created.chatId(),
                                                created.teacherId(),
                                                created.jobId(),
                                                created.type(),
                                                created.payload(),
                                                created.compensation(),
                                                now,
                                                policy))
                        .toList();
        return actions.saveBatch(batch);
    }
}
