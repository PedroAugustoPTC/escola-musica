package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Versões imutáveis: só inserção (Princípio III). */
@Repository
class ReportVersionRepositoryAdapter implements ReportVersionRepository {

    private final ReportVersionJpa jpa;

    ReportVersionRepositoryAdapter(ReportVersionJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(ReportVersion version) {
        if (jpa.existsById(version.id())) {
            throw new IllegalStateException("versão é imutável");
        }
        jpa.saveAndFlush(LessonReportMapper.toEntity(version));
    }

    @Override
    public Optional<ReportVersion> search(ReportType type, UUID reportId, int number) {
        return jpa.findByReportTypeAndReportIdAndNumber(type.name(), reportId, number)
                .map(LessonReportMapper::toDomain);
    }
}
