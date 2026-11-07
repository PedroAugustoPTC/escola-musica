package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Alertas em memória, com a mesma regra do índice único: um ativo por tipo e componente. */
public class InMemoryOperationalAlertRepository implements OperationalAlertRepository {

    private final List<OperationalAlert> alerts = new ArrayList<>();

    @Override
    public OpenResult openOrTouch(
            AlertType type, String component, String detailCode, Instant now) {
        for (int i = 0; i < alerts.size(); i++) {
            OperationalAlert alert = alerts.get(i);
            if (alert.active() && alert.type() == type && alert.component().equals(component)) {
                alerts.set(
                        i,
                        new OperationalAlert(
                                alert.id(),
                                type,
                                component,
                                detailCode,
                                AlertStatus.ACTIVE,
                                alert.startedAt(),
                                now,
                                null,
                                alert.occurrences() + 1));
                return new OpenResult(alert.id(), false);
            }
        }
        UUID id = GeneratorId.create();
        alerts.add(
                new OperationalAlert(
                        id, type, component, detailCode, AlertStatus.ACTIVE, now, now, null, 1));
        return new OpenResult(id, true);
    }

    @Override
    public Optional<UUID> normalize(AlertType type, String component, Instant now) {
        for (int i = 0; i < alerts.size(); i++) {
            OperationalAlert alert = alerts.get(i);
            if (alert.active() && alert.type() == type && alert.component().equals(component)) {
                alerts.set(
                        i,
                        new OperationalAlert(
                                alert.id(),
                                type,
                                component,
                                alert.detailCode(),
                                AlertStatus.NORMALIZED,
                                alert.startedAt(),
                                alert.lastSeenAt(),
                                now,
                                alert.occurrences()));
                return Optional.of(alert.id());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<OperationalAlert> findActive() {
        return alerts.stream().filter(OperationalAlert::active).toList();
    }

    @Override
    public Optional<OperationalAlert> findById(UUID id) {
        return alerts.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    @Override
    public PageResult<OperationalAlert> list(AlertStatus status, PageRequest page) {
        List<OperationalAlert> sorted =
                alerts.stream()
                        .filter(a -> status == null || a.status() == status)
                        .sorted(
                                Comparator.comparing((OperationalAlert a) -> !a.active())
                                        .thenComparing(
                                                OperationalAlert::lastSeenAt,
                                                Comparator.reverseOrder()))
                        .toList();
        return PageResult.slice(sorted, page);
    }

    @Override
    public int countActive() {
        return findActive().size();
    }

    public List<OperationalAlert> all() {
        return List.copyOf(alerts);
    }
}
