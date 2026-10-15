package br.com.felipeblaft.reports.outbound.queue;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.channel.DeliverPendingActionsUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Despachante da outbox (FR-011, FR-012): a cada {@code intervalo-despacho-ms} entrega ao
 * orquestrador os lotes devidos, com retentativa, ordem por chat e prazo (R-04 a R-06). Só existe
 * quando há {@code orquestrador-url}; vários despachantes (várias instâncias) não disputam o mesmo
 * lote ({@code SKIP LOCKED} e lease). Expõe as métricas de R-15, sem dado de aluno.
 */
@Component
@Profile("!seed")
@ConditionalOnExpression("!'${reports.telegram-channel.orchestrator-url:}'.isBlank()")
public class ChannelActionsDeliveryTask {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelActionsDeliveryTask.class);

    private final DeliverPendingActionsUseCase deliver;
    private final ChannelActionRepository actions;
    private final ClockPort clock;
    private final int batchMaxPerCycle;
    private final Duration confirmationMax;
    private final Counter acceptedDeliveries;
    private final Counter refusedDeliveries;
    private final Counter permanentFailures;

    public ChannelActionsDeliveryTask(
            DeliverPendingActionsUseCase deliver,
            ChannelActionRepository actions,
            ClockPort clock,
            MeterRegistry registration,
            ApplicationProperties properties) {
        this.deliver = deliver;
        this.actions = actions;
        this.clock = clock;
        ApplicationProperties.TelegramChannel channel = properties.telegramChannel();
        this.batchMaxPerCycle = channel.batchMaxPerCycle();
        this.confirmationMax = Duration.ofSeconds(channel.confirmationMaxSeconds());
        this.acceptedDeliveries =
                Counter.builder("channel_deliveries_total")
                        .tag("result", "accepts")
                        .register(registration);
        this.refusedDeliveries =
                Counter.builder("channel_deliveries_total")
                        .tag("result", "refused")
                        .register(registration);
        this.permanentFailures =
                Counter.builder("channel_permanent_failure_actions_total").register(registration);
        Gauge.builder("channel_pending_actions", this, t -> t.pending().quantity())
                .description("Ações do canal aguardando entrega ao orquestrador")
                .register(registration);
        Gauge.builder(
                        "channel_oldest_pending_action_seconds",
                        this,
                        t -> {
                            Instant created = t.pending().oldestCreatedAt();
                            return created == null
                                    ? 0
                                    : Math.max(
                                            0,
                                            Duration.between(created, t.clock.now()).toSeconds());
                        })
                .description("Idade da ação pendente mais antiga")
                .register(registration);
        Gauge.builder(
                        "channel_unconfirmed_actions_total",
                        this,
                        t -> t.pending().withoutConfirmation())
                .description("Ações entregues e ainda sem confirmação além da espera máxima")
                .register(registration);
    }

    private ChannelActionRepository.Pending pending() {
        return actions.pending(clock.now().minus(confirmationMax));
    }

    @Scheduled(
            fixedDelayString = "${reports.telegram-channel.dispatch-interval-ms:2000}",
            initialDelayString = "${reports.telegram-channel.dispatch-interval-ms:2000}")
    public void dispatch() {
        try {
            DeliverPendingActionsUseCase.Cycle cycle = deliver.run(batchMaxPerCycle);
            acceptedDeliveries.increment(cycle.acceptedBatches());
            refusedDeliveries.increment(cycle.refusedBatches());
            for (String actionId : cycle.permanentFailures()) {
                permanentFailures.increment();
                // sem dado de aluno: só o identificador técnico (FR-018, FR-029)
                LOG.warn("channel-action-permanent-failure actionId={}", actionId);
            }
        } catch (RuntimeException error) {
            LOG.error("channel-dispatch-failed type={}", error.getClass().getSimpleName());
        }
    }
}
