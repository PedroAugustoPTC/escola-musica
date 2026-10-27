package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelOrchestratorPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DeliveryBatch;
import br.com.felipeblaft.reports.port.DeliveryResult;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Despacha ao orquestrador os lotes de ações proativas devidos (FR-011, FR-012, FR-016, FR-018). Um
 * lote é entregue inteiro e fora de transação: o HTTP nunca segura conexão do banco. Toda gravação
 * é condicional à versão lida, de modo que uma confirmação que chegue durante o HTTP prevalece
 * sobre o aceite.
 *
 * <p>Este caso de uso <strong>não</strong> roda dentro de uma transação única (ver {@code
 * TransacaoCasoDeUsoDecorador}).
 */
public class DeliverPendingActionsUseCase {

    /**
     * @param lotesAceitos lotes que o orquestrador aceitou
     * @param lotesRecusados lotes recusados (continuam pendentes até o prazo)
     * @param descartadas ações descartadas por vínculo desfeito
     * @param falhasDefinitivas ids das ações que venceram o prazo sem aceite
     */
    public record Cycle(
            int acceptedBatches,
            int refusedBatches,
            int discarded,
            List<String> permanentFailures) {}

    private final ChannelActionRepository actions;
    private final ChannelOrchestratorPort orchestrator;
    private final JobQueuePort queue;
    private final TelegramLinkRepository links;
    private final ClockPort clock;
    private final RetryPolicy policy;
    private final Duration confirmationMax;
    private final Duration lease;

    public DeliverPendingActionsUseCase(
            ChannelActionRepository actions,
            ChannelOrchestratorPort orchestrator,
            JobQueuePort queue,
            TelegramLinkRepository links,
            ClockPort clock,
            RetryPolicy policy,
            Duration confirmationMax,
            Duration lease) {
        this.actions = Objects.requireNonNull(actions);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.queue = Objects.requireNonNull(queue);
        this.links = Objects.requireNonNull(links);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
        this.confirmationMax = Objects.requireNonNull(confirmationMax);
        this.lease = Objects.requireNonNull(lease);
    }

    /** Processa até {@code maximoLotes} lotes devidos. */
    public Cycle run(int maxBatches) {
        int accepted = 0;
        int refused = 0;
        int discarded = 0;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < maxBatches; i++) {
            Instant now = clock.now();
            List<ChannelAction> batch =
                    actions.reserveNextDueBatch(now, confirmationMax, now.plus(lease));
            if (batch.isEmpty()) {
                break;
            }
            List<ChannelAction> toDeliver = new ArrayList<>();
            for (ChannelAction action : batch) {
                int readVersion = action.version();
                if (action.expired(now)) {
                    action.expire(now);
                    if (actions.updateIfVersion(action, readVersion)) {
                        failures.add(action.id());
                        compensateDeletion(action, now);
                    }
                } else if (action.teacherId() != null
                        && links.searchByTeacherId(action.teacherId()).isEmpty()) {
                    action.discard(now);
                    if (actions.updateIfVersion(action, readVersion)) {
                        discarded++;
                    }
                } else {
                    toDeliver.add(action);
                }
            }
            if (toDeliver.isEmpty()) {
                continue;
            }
            DeliveryResult result = orchestrator.deliver(forBatch(toDeliver));
            Instant after = clock.now();
            for (ChannelAction action : toDeliver) {
                int readVersion = action.version();
                if (result.accepted()) {
                    action.markDelivered(after);
                } else {
                    action.registerDeliveryFailure(after, policy);
                }
                // false = a confirmação chegou durante o HTTP: o estado final prevalece
                if (actions.updateIfVersion(action, readVersion)
                        && action.state().finalized()
                        && !result.accepted()) {
                    failures.add(action.id());
                    compensateDeletion(action, after);
                }
            }
            if (result.accepted()) {
                accepted++;
            } else {
                refused++;
            }
        }
        return new Cycle(accepted, refused, discarded, List.copyOf(failures));
    }

    /**
     * A mensagem a apagar (o CPF no chat) que venceu o prazo gera o aviso alternativo, uma só vez
     * (id {@code <id>-c}, gravação idempotente).
     */
    private void compensateDeletion(ChannelAction action, Instant now) {
        if (action.compensatesPermanentFailure()) {
            actions.saveBatch(List.of(action.createCompensation(now, policy)));
        }
    }

    private DeliveryBatch forBatch(List<ChannelAction> batch) {
        ChannelAction first = batch.get(0);
        int attempt = batch.stream().mapToInt(ChannelAction::attempts).max().orElse(0) + 1;
        List<DeliveryBatch.Action> items =
                batch.stream()
                        .map(
                                action ->
                                        new DeliveryBatch.Action(
                                                action.id(),
                                                action.type(),
                                                action.payload(),
                                                action.jobId(),
                                                stepMessage(action).orElse(null)))
                        .toList();
        return new DeliveryBatch(
                first.batchId(), first.origin(), first.createdAt(), attempt, items);
    }

    /** Id da mensagem de etapa já conhecida do trabalho (a etapa edita no lugar). */
    private Optional<Long> stepMessage(ChannelAction action) {
        // a compensação existe justamente porque a mensagem não pôde ser editada: envia mensagem
        // nova
        if (action.type() != ChannelActionType.STEP_MESSAGE
                || action.jobId() == null
                || action.origin() == ChannelActionOrigin.COMPENSATION) {
            return Optional.empty();
        }
        return queue.searchById(action.jobId())
                .flatMap(job -> job.parameter(ProcessingJob.STEP_MESSAGE))
                .flatMap(DeliverPendingActionsUseCase::asNumber);
    }

    private static Optional<Long> asNumber(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }
}
