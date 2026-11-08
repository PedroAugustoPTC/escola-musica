package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReportInteractionRepositoryAdapter implements ReportInteractionRepository {

    private final ReportInteractionJpa jpa;
    private final JdbcTemplate jdbc;

    ReportInteractionRepositoryAdapter(ReportInteractionJpa jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public int removeText(ReportType type, UUID reportId, Instant now) {
        return jdbc.update(
                "UPDATE report_interaction SET text = NULL, text_removed_at = ?"
                        + " WHERE report_type = ? AND report_id = ? AND text IS NOT NULL",
                Timestamp.from(now),
                type.name(),
                reportId);
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
