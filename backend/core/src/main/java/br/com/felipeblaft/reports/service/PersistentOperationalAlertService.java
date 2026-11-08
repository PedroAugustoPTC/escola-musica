package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import br.com.felipeblaft.reports.port.OperationalAlertRepository.OpenResult;
import br.com.felipeblaft.reports.port.TransactionPort;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Alertas operacionais persistentes: um ativo por tipo e componente (ocorrência nova atualiza o
 * mesmo), com evento {@code ALERT_OPENED} ao abrir e {@code ALERT_NORMALIZED} ao normalizar, ambos
 * do ator de apuração. Componente e motivo são códigos validados: nenhum campo aceita aluno nem
 * texto livre.
 */
public class PersistentOperationalAlertService implements OperationalAlertService {

    public static final String OPENED_EVENT = "ALERT_OPENED";
    public static final String NORMALIZED_EVENT = "ALERT_NORMALIZED";

    private final OperationalAlertRepository alerts;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;
    private final ClockPort clock;

    public PersistentOperationalAlertService(
            OperationalAlertRepository alerts,
            AuthorshipService authorship,
            TransactionPort transaction,
            ClockPort clock) {
        this.alerts = Objects.requireNonNull(alerts);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void report(AlertType type, String component, String detailCode) {
        Objects.requireNonNull(type);
        OperationalAlert.requireComponent(component);
        OperationalAlert.requireDetail(detailCode);
        Instant now = clock.now();
        transaction.run(
                () -> {
                    OpenResult result = alerts.openOrTouch(type, component, detailCode, now);
                    if (result.opened()) {
                        event(OPENED_EVENT, result.alertId());
                    }
                });
    }

    @Override
    public void resolve(AlertType type, String component) {
        Objects.requireNonNull(type);
        OperationalAlert.requireComponent(component);
        Instant now = clock.now();
        transaction.run(
                () -> {
                    Optional<UUID> normalized = alerts.normalize(type, component, now);
                    normalized.ifPresent(id -> event(NORMALIZED_EVENT, id));
                });
    }

    private void event(String name, UUID alertId) {
        authorship.recordEvent(
                Actor.alertEvaluation(),
                name,
                AuthorshipTargetType.ALERT,
                alertId,
                null,
                AuthorshipResult.SUCCESS,
                null,
                GeneratorId.create().toString());
    }
}
