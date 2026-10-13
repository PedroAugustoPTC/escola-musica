package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import java.util.Optional;
import java.util.UUID;

/** Versões imutáveis: só inserção e leitura. */
public interface ReportVersionRepository {

    void save(ReportVersion version);

    Optional<ReportVersion> search(ReportType type, UUID reportId, int number);
}
