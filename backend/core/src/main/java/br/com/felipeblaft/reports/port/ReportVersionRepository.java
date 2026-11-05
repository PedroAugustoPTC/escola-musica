package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Versões imutáveis: só inserção e leitura. */
public interface ReportVersionRepository {

    void save(ReportVersion version);

    Optional<ReportVersion> search(ReportType type, UUID reportId, int number);

    /**
     * Esvazia o conteúdo das versões do relatório (anonimização, FR-013), marcando {@code
     * content_removed_at}; devolve quantas versões foram tratadas. A retenção do aprovado nunca
     * chama isto.
     */
    int markContentRemoved(ReportType type, UUID reportId, Instant now);
}
