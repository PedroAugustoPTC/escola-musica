package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos alertas operacionais (FR-055, FR-056). */
public interface OperationalAlertRepository {

    /** Resultado de abrir ou atualizar: o alerta e se acabou de ser aberto. */
    record OpenResult(UUID alertId, boolean opened) {}

    /**
     * Abre o alerta ou, se já há um ativo do mesmo tipo e componente, soma a ocorrência e atualiza
     * a última vez vista. Seguro sob concorrência: nunca há dois ativos iguais.
     */
    OpenResult openOrTouch(AlertType type, String component, String detailCode, Instant now);

    /** Normaliza o alerta ativo do tipo e componente, se houver, e devolve o seu id. */
    Optional<UUID> normalize(AlertType type, String component, Instant now);

    List<OperationalAlert> findActive();

    Optional<OperationalAlert> findById(UUID id);

    /** Ativos primeiro, depois por última ocorrência decrescente. */
    PageResult<OperationalAlert> list(AlertStatus status, PageRequest page);

    int countActive();
}
