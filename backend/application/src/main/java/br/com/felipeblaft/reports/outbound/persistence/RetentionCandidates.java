package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seleção grossa dos relatórios com algo a tratar pela retenção, em lotes por id. O caso de uso
 * reavalia cada candidato sob bloqueio, então a consulta só precisa não deixar passar nenhum.
 */
final class RetentionCandidates {

    private RetentionCandidates() {}

    /** {@code table} é uma constante interna ({@code lesson_report} ou {@code semester_report}). */
    static List<UUID> reportIds(
            JdbcTemplate jdbc,
            String table,
            RetentionPolicy policy,
            Instant now,
            int limit,
            UUID afterId) {
        Timestamp approvedCutoff = cutoff(now, policy.transcriptionDays().value());
        Timestamp cancelledCutoff = cutoff(now, policy.cancelledReportDays().value());
        String sql =
                "SELECT id FROM "
                        + table
                        + " WHERE anonymized_at IS NULL AND ("
                        + " (status = 'CANCELLED' AND cancelled_at <= ?)"
                        + " OR (status = 'APPROVED' AND source_removed_at IS NULL"
                        + " AND approved_at <= ?))"
                        + " AND (?::uuid IS NULL OR id > ?::uuid) ORDER BY id LIMIT ?";
        return jdbc.queryForList(
                sql, UUID.class, cancelledCutoff, approvedCutoff, afterId, afterId, limit);
    }

    static Timestamp cutoff(Instant now, int days) {
        return Timestamp.from(now.minus(Duration.ofDays(days)));
    }
}
