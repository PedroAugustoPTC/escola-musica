package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAuditTrail;
import br.com.felipeblaft.reports.doubles.InMemoryOperationalAlertRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Alertas persistentes (FR-055, FR-056): abrir, somar ocorrência, normalizar e eventos. */
class OperationalAlertServiceTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final InMemoryOperationalAlertRepository repository =
            new InMemoryOperationalAlertRepository();
    private final PersistentOperationalAlertService service =
            new PersistentOperationalAlertService(
                    repository,
                    new AuthorshipService(trail, clock),
                    new ImmediateTransaction(),
                    clock);

    private List<AuthorshipRecord> events(String name) {
        return trail.records().stream().filter(r -> r.operation().equals(name)).toList();
    }

    @Test
    @DisplayName("a primeira ocorrência abre o alerta e grava ALERT_OPENED do ator de apuração")
    void firstOccurrenceOpens() {
        service.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");

        assertThat(repository.findActive()).hasSize(1);
        OperationalAlert alert = repository.findActive().getFirst();
        assertThat(alert.occurrences()).isEqualTo(1);
        assertThat(alert.startedAt()).isEqualTo(clock.now());
        AuthorshipRecord event = events("ALERT_OPENED").getFirst();
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.ALERT);
        assertThat(event.targetId()).isEqualTo(alert.id());
        assertThat(event.origin()).isEqualTo(ActionOrigin.ALERT_EVALUATION);
    }

    @Test
    @DisplayName("ocorrência nova atualiza o mesmo alerta e não grava outro evento de abertura")
    void repeatedOccurrenceUpdatesTheSameAlert() {
        service.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
        clock.advance(Duration.ofMinutes(3));
        service.report(AlertType.STORAGE_FAILED, "pdf-storage", "READ_FAILED");

        assertThat(repository.all()).hasSize(1);
        OperationalAlert alert = repository.findActive().getFirst();
        assertThat(alert.occurrences()).isEqualTo(2);
        assertThat(alert.lastSeenAt()).isEqualTo(clock.now());
        assertThat(alert.startedAt()).isBefore(alert.lastSeenAt());
        assertThat(events("ALERT_OPENED")).hasSize(1);
    }

    @Test
    @DisplayName("componentes diferentes do mesmo tipo são alertas diferentes")
    void differentComponentsAreDifferentAlerts() {
        service.report(AlertType.AI_PROVIDER_FAILING, "provider-a", "CONSECUTIVE_FAILURES");
        service.report(AlertType.AI_PROVIDER_FAILING, "provider-b", "CONSECUTIVE_FAILURES");

        assertThat(repository.findActive()).hasSize(2);
    }

    @Test
    @DisplayName("resolve normaliza, grava ALERT_NORMALIZED e deixa de contar como ativo")
    void resolveNormalizes() {
        service.report(AlertType.RETENTION_FAILED, "retention", "RUN_FAILED");
        clock.advance(Duration.ofMinutes(1));

        service.resolve(AlertType.RETENTION_FAILED, "retention");

        assertThat(repository.countActive()).isZero();
        OperationalAlert alert = repository.all().getFirst();
        assertThat(alert.status()).isEqualTo(AlertStatus.NORMALIZED);
        assertThat(alert.normalizedAt()).isEqualTo(clock.now());
        assertThat(events("ALERT_NORMALIZED")).hasSize(1);
    }

    @Test
    @DisplayName("resolver sem alerta ativo não grava nada")
    void resolveWithoutActiveAlertDoesNothing() {
        service.resolve(AlertType.RETENTION_FAILED, "retention");

        assertThat(trail.records()).isEmpty();
    }

    @Test
    @DisplayName("depois de normalizado, a condição que volta abre um alerta novo")
    void reopensAsNewAlertAfterNormalization() {
        service.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
        service.resolve(AlertType.STORAGE_FAILED, "pdf-storage");
        service.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");

        assertThat(repository.all()).hasSize(2);
        assertThat(repository.findActive()).hasSize(1);
        assertThat(events("ALERT_OPENED")).hasSize(2);
    }

    @Test
    @DisplayName("componente e motivo só aceitam código: id de aluno e texto livre são recusados")
    void freeTextIsRefused() {
        assertThatThrownBy(
                        () -> service.report(AlertType.STORAGE_FAILED, "Jane Doe", "WRITE_FAILED"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.report(
                                        AlertType.STORAGE_FAILED,
                                        "pdf-storage",
                                        "failed to write the student pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.report(
                                        AlertType.STORAGE_FAILED,
                                        "00000000-0000-4000-8000-0000000000b1 extra",
                                        "WRITE_FAILED"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.all()).isEmpty();
    }

    @Test
    @DisplayName("nome de provedor vira componente válido")
    void providerNameBecomesAComponent() {
        assertThat(OperationalAlert.componentOf("API.Example.com/Model-1"))
                .isEqualTo("api.example.com-model-1");
        assertThat(OperationalAlert.componentOf(null)).isEqualTo("unknown");
        assertThat(OperationalAlert.componentOf("x".repeat(100))).hasSize(64);
    }
}
