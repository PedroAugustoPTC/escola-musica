package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.port.AlertHistoryRetentionPort;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Expurgo dos alertas operacionais já normalizados (FR-014). */
@Repository
class AlertHistoryRetentionAdapter implements AlertHistoryRetentionPort {

    private final JdbcTemplate jdbc;

    AlertHistoryRetentionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int purgeNormalizedBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM operational_alert WHERE status = 'NORMALIZED' AND normalized_at < ?",
                Timestamp.from(cutoff));
    }
}
