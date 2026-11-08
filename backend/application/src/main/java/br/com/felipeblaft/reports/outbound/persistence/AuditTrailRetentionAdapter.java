package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.port.AuditTrailRetentionPort;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Expurgo da trilha (FR-043): chama a função {@code purge_authorship_record} do banco, que impõe o
 * piso de 365 dias, remove o prefixo da cadeia e grava a âncora na mesma transação. A aplicação não
 * tem {@code DELETE} na tabela: só esta função remove.
 */
@Repository
class AuditTrailRetentionAdapter implements AuditTrailRetentionPort {

    private final JdbcTemplate jdbc;

    AuditTrailRetentionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        Integer removed =
                jdbc.queryForObject(
                        "SELECT purge_authorship_record(?)", Integer.class, Timestamp.from(cutoff));
        return removed == null ? 0 : removed;
    }
}
