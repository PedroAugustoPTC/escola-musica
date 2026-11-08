package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Versões imutáveis: só inserção (Princípio III). Única exceção: {@code markContentRemoved} esvazia o
 * conteúdo na eliminação a pedido e na retenção de relatório cancelado (direito do titular, decisão
 * D7 do plano da feature 007).
 */
@Repository
class ReportVersionRepositoryAdapter implements ReportVersionRepository {

    private final ReportVersionJpa jpa;
    private final JdbcTemplate jdbc;

    ReportVersionRepositoryAdapter(ReportVersionJpa jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public int markContentRemoved(ReportType type, UUID reportId, Instant now) {
        return jdbc.update(
                "UPDATE report_version SET content = '{}'::jsonb, content_removed_at = ?"
                        + " WHERE report_type = ? AND report_id = ? AND content_removed_at IS NULL",
                Timestamp.from(now),
                type.name(),
                reportId);
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
