package br.com.felipeblaft.reports.usecase.operations;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.doubles.FakeOperationalSignals;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAuditTrail;
import br.com.felipeblaft.reports.doubles.InMemoryOperationalAlertRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.port.OperationalSignalQueryPort.ProviderWindow;
import br.com.felipeblaft.reports.port.OperationalSignalQueryPort.QueueSnapshot;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.PersistentOperationalAlertService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Apuração de alertas (FR-055): provedor de IA, fila do canal e armazenamento. */
class EvaluateOperationalAlertsUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final InMemoryOperationalAlertRepository repository =
            new InMemoryOperationalAlertRepository();
    private final PersistentOperationalAlertService alerts =
            new PersistentOperationalAlertService(
                    repository,
                    new AuthorshipService(trail, clock),
                    new ImmediateTransaction(),
                    clock);
    private final FakeOperationalSignals signals = new FakeOperationalSignals();
    private final EvaluateOperationalAlertsUseCase evaluate =
            new EvaluateOperationalAlertsUseCase(
                    signals, repository, alerts, AlertThresholds.from(Map.of()), clock);

    private long openedEvents() {
        return trail.records().stream()
                .filter(r -> r.operation().equals("ALERT_OPENED"))
                .count();
    }

    @Test
    @DisplayName("três falhas seguidas abrem AI_PROVIDER_FAILING do provedor")
    void consecutiveFailuresOpenTheAlert() {
        signals.providers.add(new ProviderWindow("provider-a", 10, 3, 3));

        evaluate.run();

        assertThat(repository.findActive()).hasSize(1);
        OperationalAlert alert = repository.findActive().getFirst();
        assertThat(alert.type()).isEqualTo(AlertType.AI_PROVIDER_FAILING);
        assertThat(alert.component()).isEqualTo("provider-a");
        assertThat(alert.detailCode()).isEqualTo("CONSECUTIVE_FAILURES");
        assertThat(signals.lastSince).isEqualTo(clock.now().minus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("mais de 20% de falhas na janela abrem o alerta; exatamente 20% não")
    void failureRateAboveTheLimitOpensTheAlert() {
        signals.providers.add(new ProviderWindow("provider-a", 10, 2, 0));
        evaluate.run();
        assertThat(repository.findActive()).isEmpty();

        signals.providers.clear();
        signals.providers.add(new ProviderWindow("provider-a", 10, 3, 0));
        evaluate.run();
        assertThat(repository.findActive()).hasSize(1);
        assertThat(repository.findActive().getFirst().detailCode()).isEqualTo("HIGH_FAILURE_RATE");
    }

    @Test
    @DisplayName("ocorrência nova atualiza o mesmo alerta, sem abrir outro")
    void newOccurrenceUpdatesTheSameAlert() {
        signals.providers.add(new ProviderWindow("provider-a", 10, 3, 3));
        evaluate.run();
        clock.advance(Duration.ofMinutes(1));

        evaluate.run();

        assertThat(repository.all()).hasSize(1);
        assertThat(repository.findActive().getFirst().occurrences()).isEqualTo(2);
        assertThat(openedEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("o provedor que volta a funcionar, ou some da janela, normaliza o alerta")
    void providerRecoveryNormalizes() {
        signals.providers.add(new ProviderWindow("provider-a", 10, 3, 3));
        evaluate.run();

        signals.providers.clear();
        signals.providers.add(new ProviderWindow("provider-a", 20, 3, 0));
        clock.advance(Duration.ofMinutes(1));
        evaluate.run();

        assertThat(repository.countActive()).isZero();
        assertThat(
                        trail.records().stream()
                                .filter(r -> r.operation().equals("ALERT_NORMALIZED"))
                                .count())
                .isEqualTo(1);

        signals.providers.clear();
        signals.providers.add(new ProviderWindow("provider-b", 5, 5, 5));
        evaluate.run();
        signals.providers.clear();
        evaluate.run();
        assertThat(repository.countActive()).isZero();
    }

    @Test
    @DisplayName("provedores falhando ao mesmo tempo geram um alerta cada")
    void eachProviderHasItsOwnAlert() {
        signals.providers.add(new ProviderWindow("provider-a", 4, 4, 4));
        signals.providers.add(new ProviderWindow("provider-b", 4, 4, 4));

        evaluate.run();

        assertThat(repository.findActive()).hasSize(2);
    }

    @Test
    @DisplayName("fila com item esperando há mais de 5 minutos abre CHANNEL_QUEUE_BACKLOG")
    void oldQueueItemOpensTheAlert() {
        signals.queue = new QueueSnapshot(2, clock.now().minus(Duration.ofMinutes(6)));

        evaluate.run();

        OperationalAlert alert = repository.findActive().getFirst();
        assertThat(alert.type()).isEqualTo(AlertType.CHANNEL_QUEUE_BACKLOG);
        assertThat(alert.detailCode()).isEqualTo("OLD_ITEM");
    }

    @Test
    @DisplayName("fila com mais de 50 itens abre o alerta; 50 itens recentes não")
    void queueWithManyItemsOpensTheAlert() {
        signals.queue = new QueueSnapshot(50, clock.now().minus(Duration.ofMinutes(1)));
        evaluate.run();
        assertThat(repository.findActive()).isEmpty();

        signals.queue = new QueueSnapshot(51, clock.now().minus(Duration.ofMinutes(1)));
        evaluate.run();
        assertThat(repository.findActive().getFirst().detailCode()).isEqualTo("TOO_MANY_ITEMS");
    }

    @Test
    @DisplayName("a fila que esvazia normaliza o alerta")
    void drainedQueueNormalizes() {
        signals.queue = new QueueSnapshot(2, clock.now().minus(Duration.ofMinutes(6)));
        evaluate.run();

        signals.queue = new QueueSnapshot(0, null);
        evaluate.run();

        assertThat(repository.countActive()).isZero();
    }

    @Test
    @DisplayName("STORAGE_FAILED normaliza só depois de 15 minutos sem nova ocorrência")
    void storageNormalizesAfterTheQuietPeriod() {
        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");

        clock.advance(Duration.ofMinutes(14));
        evaluate.run();
        assertThat(repository.countActive()).isEqualTo(1);

        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
        clock.advance(Duration.ofMinutes(14));
        evaluate.run();
        assertThat(repository.countActive()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        evaluate.run();
        assertThat(repository.countActive()).isZero();
    }

    @Test
    @DisplayName("RETENTION_FAILED e AUDIT_CHAIN_BROKEN não são normalizados pela apuração")
    void otherTypesAreNotNormalizedByTheEvaluation() {
        alerts.report(AlertType.RETENTION_FAILED, "retention", "RUN_FAILED");
        alerts.report(AlertType.AUDIT_CHAIN_BROKEN, "audit-trail", "CHAIN_BROKEN");

        clock.advance(Duration.ofHours(2));
        evaluate.run();

        assertThat(repository.countActive()).isEqualTo(2);
    }
}
