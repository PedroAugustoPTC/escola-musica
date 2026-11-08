package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Alertas operacionais (FR-055, FR-056). Abrir ou somar ocorrência é uma única instrução com {@code
 * ON CONFLICT} sobre o índice único parcial dos ativos: sob concorrência nunca há dois ativos
 * iguais.
 */
@Repository
class OperationalAlertRepositoryAdapter implements OperationalAlertRepository {

    private static final String COLUMNS =
            "id, type, component, detail_code, status, started_at, last_seen_at, normalized_at,"
                    + " occurrences";

    private static final RowMapper<OperationalAlert> MAPPER =
            OperationalAlertRepositoryAdapter::map;

    private final JdbcTemplate jdbc;

    OperationalAlertRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OpenResult openOrTouch(
            AlertType type, String component, String detailCode, Instant now) {
        Timestamp at = Timestamp.from(now);
        return jdbc.queryForObject(
                "INSERT INTO operational_alert (id, type, component, detail_code, status,"
                        + " started_at, last_seen_at, occurrences) VALUES (?, ?, ?, ?, 'ACTIVE',"
                        + " ?, ?, 1) ON CONFLICT (type, component) WHERE status = 'ACTIVE' DO"
                        + " UPDATE SET last_seen_at = EXCLUDED.last_seen_at, detail_code ="
                        + " EXCLUDED.detail_code, occurrences = operational_alert.occurrences + 1"
                        + " RETURNING id, (xmax = 0) AS inserted",
                (rs, row) ->
                        new OpenResult(rs.getObject("id", UUID.class), rs.getBoolean("inserted")),
                GeneratorId.create(),
                type.name(),
                component,
                detailCode,
                at,
                at);
    }

    @Override
    public Optional<UUID> normalize(AlertType type, String component, Instant now) {
        List<UUID> ids =
                jdbc.query(
                        "UPDATE operational_alert SET status = 'NORMALIZED', normalized_at = ?"
                                + " WHERE type = ? AND component = ? AND status = 'ACTIVE'"
                                + " RETURNING id",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        Timestamp.from(now),
                        type.name(),
                        component);
        return ids.stream().findFirst();
    }

    @Override
    public List<OperationalAlert> findActive() {
        return jdbc.query(
                "SELECT "
                        + COLUMNS
                        + " FROM operational_alert WHERE status = 'ACTIVE'"
                        + " ORDER BY last_seen_at DESC",
                MAPPER);
    }

    @Override
    public Optional<OperationalAlert> findById(UUID id) {
        return jdbc
                .query("SELECT " + COLUMNS + " FROM operational_alert WHERE id = ?", MAPPER, id)
                .stream()
                .findFirst();
    }

    @Override
    public PageResult<OperationalAlert> list(AlertStatus status, PageRequest page) {
        String where = status == null ? "" : " WHERE status = '" + status.name() + "'";
        Long total =
                jdbc.queryForObject("SELECT count(*) FROM operational_alert" + where, Long.class);
        List<OperationalAlert> items =
                jdbc.query(
                        "SELECT "
                                + COLUMNS
                                + " FROM operational_alert"
                                + where
                                + " ORDER BY (status = 'ACTIVE') DESC, last_seen_at DESC, id"
                                + " LIMIT ? OFFSET ?",
                        MAPPER,
                        page.size(),
                        page.offset());
        return new PageResult<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    @Override
    public int countActive() {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM operational_alert WHERE status = 'ACTIVE'",
                        Integer.class);
        return count == null ? 0 : count;
    }

    private static OperationalAlert map(ResultSet rs, int row) throws SQLException {
        Timestamp normalized = rs.getTimestamp("normalized_at");
        return new OperationalAlert(
                rs.getObject("id", UUID.class),
                AlertType.valueOf(rs.getString("type")),
                rs.getString("component"),
                rs.getString("detail_code"),
                AlertStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("last_seen_at").toInstant(),
                normalized == null ? null : normalized.toInstant(),
                rs.getInt("occurrences"));
    }
}
