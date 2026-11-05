package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.privacy.RetentionItemType;
import br.com.felipeblaft.reports.domain.privacy.RetentionRunStatus;
import br.com.felipeblaft.reports.port.RetentionRunRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Execuções da retenção e seus itens (FR-015); só identificadores, quantidades e prazos. */
@Repository
class RetentionRunRepositoryAdapter implements RetentionRunRepository {

    private final JdbcTemplate jdbc;

    RetentionRunRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> start(Instant now, Map<String, Integer> snapshot, Duration staleAfter) {
        jdbc.update(
                "UPDATE retention_run SET status = 'INTERRUPTED', finished_at = ?"
                        + " WHERE status = 'RUNNING' AND started_at <= ?",
                Timestamp.from(now),
                Timestamp.from(now.minus(staleAfter)));
        UUID id = GeneratorId.create();
        try {
            int inserted =
                    jdbc.update(
                            "INSERT INTO retention_run (id, started_at, status, policy_snapshot)"
                                    + " SELECT ?, ?, 'RUNNING', ?::jsonb"
                                    + " WHERE NOT EXISTS"
                                    + " (SELECT 1 FROM retention_run WHERE status = 'RUNNING')",
                            id,
                            Timestamp.from(now),
                            JsonPersistence.write(snapshot));
            return inserted == 1 ? Optional.of(id) : Optional.empty();
        } catch (DuplicateKeyException race) {
            return Optional.empty();
        }
    }

    @Override
    public void addItem(UUID runId, RetentionItemType type, UUID targetId, int count) {
        jdbc.update(
                "INSERT INTO retention_run_item (id, run_id, item_type, target_id, count)"
                        + " VALUES (?, ?, ?, ?, ?)",
                GeneratorId.create(),
                runId,
                type.name(),
                targetId,
                count);
    }

    @Override
    public void finish(UUID runId, Instant now, RetentionRunStatus status, String errorCode) {
        jdbc.update(
                "UPDATE retention_run SET status = ?, error_code = ?, finished_at = ? WHERE id = ?",
                status.name(),
                errorCode,
                Timestamp.from(now),
                runId);
    }

    @Override
    public int purgeStartedBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM retention_run WHERE started_at < ? AND status <> 'RUNNING'",
                Timestamp.from(cutoff));
    }
}
