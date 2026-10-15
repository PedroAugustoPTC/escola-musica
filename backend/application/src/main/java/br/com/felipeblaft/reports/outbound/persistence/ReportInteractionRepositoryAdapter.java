package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class ReportInteractionRepositoryAdapter implements ReportInteractionRepository {

    private final ReportInteractionJpa jpa;

    ReportInteractionRepositoryAdapter(ReportInteractionJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(ReportInteraction interaction) {
        jpa.saveAndFlush(LessonReportMapper.toEntity(interaction));
    }

    @Override
    public Optional<ReportInteraction> searchById(UUID id) {
        return jpa.findById(id).map(LessonReportMapper::toDomain);
    }

    @Override
    public Optional<ReportInteraction> last(ReportType type, UUID reportId) {
        return jpa.findFirstByReportTypeAndReportIdOrderByCreatedAtDescIdDesc(type.name(), reportId)
                .map(LessonReportMapper::toDomain);
    }
}
