package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReportInteractionRepository {

    void save(ReportInteraction interaction);

    Optional<ReportInteraction> searchById(UUID id);

    Optional<ReportInteraction> last(ReportType type, UUID reportId);

    /**
     * Remove o texto das interações do relatório (a linha fica, com {@code text_removed_at});
     * devolve quantas foram tratadas.
     */
    int removeText(ReportType type, UUID reportId, Instant now);
}
