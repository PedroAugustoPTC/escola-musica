package br.com.felipeblaft.reports.usecase.operations;

import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import br.com.felipeblaft.reports.port.OperationalSignalQueryPort;
import br.com.felipeblaft.reports.port.OperationalSignalQueryPort.ProviderWindow;
import br.com.felipeblaft.reports.port.OperationalSignalQueryPort.QueueSnapshot;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Apuração periódica de alertas (FR-055), chamada só pela tarefa agendada: abre ou atualiza o
 * alerta de provedor de IA (falhas seguidas ou taxa alta na janela) e o de fila do canal (item
 * esperando demais ou itens demais), e normaliza o que deixou de valer. {@code STORAGE_FAILED}
 * normaliza depois do período quieto sem nova ocorrência; os demais tipos são normalizados onde a
 * condição some. Não há autorização: não é uma operação do usuário.
 */
public class EvaluateOperationalAlertsUseCase {

    public static final String QUEUE_COMPONENT = "channel-queue";

    private final OperationalSignalQueryPort signals;
    private final OperationalAlertRepository repository;
    private final OperationalAlertService alerts;
    private final AlertThresholds thresholds;
    private final ClockPort clock;

    public EvaluateOperationalAlertsUseCase(
            OperationalSignalQueryPort signals,
            OperationalAlertRepository repository,
            OperationalAlertService alerts,
            AlertThresholds thresholds,
            ClockPort clock) {
        this.signals = Objects.requireNonNull(signals);
        this.repository = Objects.requireNonNull(repository);
        this.alerts = Objects.requireNonNull(alerts);
        this.thresholds = Objects.requireNonNull(thresholds);
        this.clock = Objects.requireNonNull(clock);
    }

    public void run() {
        Instant now = clock.now();
        evaluateProviders(now);
        evaluateQueue(now);
        normalizeQuietStorage(now);
    }

    private void evaluateProviders(Instant now) {
        Set<String> failing = new HashSet<>();
        for (ProviderWindow window : signals.providerWindows(now.minus(thresholds.providerWindow()))) {
            if (failing(window)) {
                String component = OperationalAlert.componentOf(window.provider());
                failing.add(component);
                alerts.report(AlertType.AI_PROVIDER_FAILING, component, reason(window));
            }
        }
        repository.findActive().stream()
                .filter(alert -> alert.type() == AlertType.AI_PROVIDER_FAILING)
                .filter(alert -> !failing.contains(alert.component()))
                .forEach(alert -> alerts.resolve(alert.type(), alert.component()));
    }

    private boolean failing(ProviderWindow window) {
        boolean consecutive =
                window.trailingFailures() >= thresholds.providerConsecutiveFailures().value();
        boolean rate =
                window.total() > 0
                        && window.failures() * 100L
                                > (long) thresholds.providerFailureRatePercent().value()
                                        * window.total();
        return consecutive || rate;
    }

    private String reason(ProviderWindow window) {
        return window.trailingFailures() >= thresholds.providerConsecutiveFailures().value()
                ? "CONSECUTIVE_FAILURES"
                : "HIGH_FAILURE_RATE";
    }

    private void evaluateQueue(Instant now) {
        QueueSnapshot queue = signals.queueSnapshot();
        boolean old =
                queue.oldestPendingAt() != null
                        && Duration.between(queue.oldestPendingAt(), now)
                                        .compareTo(thresholds.queueMaxAge())
                                > 0;
        boolean many = queue.pendingCount() > thresholds.queueMaxItems().value();
        if (old || many) {
            alerts.report(
                    AlertType.CHANNEL_QUEUE_BACKLOG,
                    QUEUE_COMPONENT,
                    old ? "OLD_ITEM" : "TOO_MANY_ITEMS");
        } else {
            alerts.resolve(AlertType.CHANNEL_QUEUE_BACKLOG, QUEUE_COMPONENT);
        }
    }

    private void normalizeQuietStorage(Instant now) {
        Instant limit = now.minus(thresholds.storageQuiet());
        repository.findActive().stream()
                .filter(alert -> alert.type() == AlertType.STORAGE_FAILED)
                .filter(alert -> !alert.lastSeenAt().isAfter(limit))
                .forEach(alert -> alerts.resolve(alert.type(), alert.component()));
    }
}
