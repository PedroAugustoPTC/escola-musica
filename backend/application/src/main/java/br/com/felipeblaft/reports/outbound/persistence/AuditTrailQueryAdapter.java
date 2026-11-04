package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.audit.AuditAnchor;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.domain.audit.AuditTrailEntry;
import br.com.felipeblaft.reports.domain.audit.AuditTrailFilter;
import br.com.felipeblaft.reports.domain.audit.AuditVerification;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Leitura da trilha de auditoria (FR-045). Os parâmetros são sempre ligados; o único texto montado
 * é a escolha de cláusulas fixas. Os rótulos (nome do autor e do alvo) são resolvidos na leitura
 * por junção e nunca gravados na trilha; aluno eliminado volta sem nome e marcado. Filtrar por um
 * aluno traz também os eventos dos relatórios e dos consentimentos dele (SC-007).
 */
@Repository
class AuditTrailQueryAdapter implements AuditTrailQueryPort {

    private static final String SELECT =
            "SELECT r.id, r.sequence, r.occurred_at, r.author_id, r.author_role, r.origin,"
                    + " r.operation, r.target_type, r.target_id, r.result, r.error_code,"
                    + " r.document_version, r.item_count, (r.origin_hash IS NOT NULL) AS has_origin_hash,"
                    + " COALESCE(t.name, a.name) AS author_label,"
                    + " COALESCE(st.name, tt.name, ta.name) AS target_label,"
                    + " (st.erased_at IS NOT NULL) AS target_erased"
                    + " FROM authorship_record r"
                    + " LEFT JOIN teacher t ON r.author_role = 'TEACHER' AND t.id = r.author_id"
                    + " LEFT JOIN administrator a ON r.author_role = 'ADMINISTRATOR' AND a.id = r.author_id"
                    + " LEFT JOIN student st ON r.target_type = 'STUDENT' AND st.id = r.target_id"
                    + " LEFT JOIN teacher tt ON r.target_type = 'TEACHER' AND tt.id = r.target_id"
                    + " LEFT JOIN administrator ta ON r.target_type = 'ADMINISTRATOR' AND ta.id = r.target_id";

    private final JdbcTemplate jdbc;
    private final ZoneId schoolZone;

    AuditTrailQueryAdapter(JdbcTemplate jdbc, ApplicationProperties properties) {
        this.jdbc = jdbc;
        this.schoolZone = properties.school().timezone();
    }

    @Override
    public PageResult<AuditTrailEntry> query(AuditTrailFilter filter) {
        List<Object> parameters = new ArrayList<>();
        String where = where(filter, parameters);
        Long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM authorship_record r" + where,
                        Long.class,
                        parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(filter.page().size());
        pageParameters.add(filter.page().offset());
        List<AuditTrailEntry> items =
                jdbc.query(
                        SELECT + where + " ORDER BY r.sequence DESC LIMIT ? OFFSET ?",
                        (rows, index) -> entry(rows),
                        pageParameters.toArray());
        return new PageResult<>(
                items, filter.page().page(), filter.page().size(), total == null ? 0 : total);
    }

    /**
     * Cláusulas fixas; o período vai do início do dia inicial ao início do dia seguinte ao final.
     */
    private String where(AuditTrailFilter filter, List<Object> parameters) {
        StringBuilder where = new StringBuilder(" WHERE r.occurred_at >= ? AND r.occurred_at < ?");
        parameters.add(java.sql.Timestamp.from(start(filter.from())));
        parameters.add(java.sql.Timestamp.from(start(filter.to().plusDays(1))));
        if (filter.actorId() != null) {
            where.append(" AND r.author_id = ?");
            parameters.add(filter.actorId());
        }
        if (filter.actorRole() != null) {
            where.append(" AND r.author_role = ?");
            parameters.add(filter.actorRole().name());
        }
        if (filter.origin() != null) {
            where.append(" AND r.origin = ?");
            parameters.add(filter.origin().name());
        }
        if (filter.operation() != null) {
            where.append(" AND r.operation = ?");
            parameters.add(filter.operation());
        }
        if (filter.result() != null) {
            where.append(" AND r.result = ?");
            parameters.add(filter.result().name());
        }
        if (filter.targetId() != null) {
            where.append(
                    " AND (r.target_id = ?"
                            + " OR (r.target_type = 'LESSON_REPORT' AND r.target_id IN"
                            + " (SELECT id FROM lesson_report WHERE student_id = ?))"
                            + " OR (r.target_type = 'SEMESTER_REPORT' AND r.target_id IN"
                            + " (SELECT id FROM semester_report WHERE student_id = ?))"
                            + " OR (r.target_type = 'CONSENT' AND r.filter_target_id = ?))");
            for (int i = 0; i < 4; i++) {
                parameters.add(filter.targetId());
            }
        }
        return where.toString();
    }

    private Instant start(LocalDate day) {
        return day.atStartOfDay(schoolZone).toInstant();
    }

    private static AuditTrailEntry entry(ResultSet rows) throws SQLException {
        String role = rows.getString("author_role");
        int documentVersion = rows.getInt("document_version");
        boolean noVersion = rows.wasNull();
        int itemCount = rows.getInt("item_count");
        boolean noCount = rows.wasNull();
        return new AuditTrailEntry(
                rows.getObject("id", UUID.class),
                rows.getLong("sequence"),
                rows.getTimestamp("occurred_at").toInstant(),
                rows.getObject("author_id", UUID.class),
                role == null ? null : Role.valueOf(role),
                rows.getString("author_label"),
                ActionOrigin.valueOf(rows.getString("origin")),
                rows.getString("operation"),
                rows.getString("target_type"),
                rows.getObject("target_id", UUID.class),
                rows.getString("target_label"),
                rows.getBoolean("target_erased"),
                noVersion ? null : documentVersion,
                noCount ? null : itemCount,
                AuthorshipResult.valueOf(rows.getString("result")),
                rows.getString("error_code"),
                rows.getBoolean("has_origin_hash"));
    }

    @Override
    public List<AuditChain.Link> chainFrom(long afterSequence, int batchSize) {
        return jdbc.query(
                "SELECT sequence, occurred_at, author_id, author_role, origin, operation, target_type,"
                        + " target_id, result, error_code, origin_hash, correlation_id,"
                        + " filter_teacher_id, filter_report_type, filter_period_start,"
                        + " filter_period_end, filter_operation, filter_actor_id, filter_target_id,"
                        + " page_number, document_version, item_count, previous_hash, record_hash"
                        + " FROM authorship_record WHERE sequence > ? ORDER BY sequence LIMIT ?",
                (rows, index) -> link(rows),
                afterSequence,
                batchSize);
    }

    private static AuditChain.Link link(ResultSet rows) throws SQLException {
        return new AuditChain.Link(
                new AuditChain.Fields(
                        rows.getLong("sequence"),
                        rows.getTimestamp("occurred_at").toInstant(),
                        rows.getObject("author_id", UUID.class),
                        rows.getString("author_role"),
                        rows.getString("origin"),
                        rows.getString("operation"),
                        rows.getString("target_type"),
                        rows.getObject("target_id", UUID.class),
                        rows.getString("result"),
                        rows.getString("error_code"),
                        rows.getString("origin_hash"),
                        rows.getString("correlation_id"),
                        rows.getObject("filter_teacher_id", UUID.class),
                        rows.getString("filter_report_type"),
                        date(rows, "filter_period_start"),
                        date(rows, "filter_period_end"),
                        rows.getString("filter_operation"),
                        rows.getObject("filter_actor_id", UUID.class),
                        rows.getObject("filter_target_id", UUID.class),
                        integer(rows, "page_number"),
                        integer(rows, "document_version"),
                        integer(rows, "item_count")),
                rows.getString("previous_hash"),
                rows.getString("record_hash"));
    }

    private static LocalDate date(ResultSet rows, String column) throws SQLException {
        java.sql.Date value = rows.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Integer integer(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    @Override
    public Optional<AuditAnchor> latestAnchor() {
        return jdbc
                .query(
                        "SELECT purged_until_sequence, last_record_hash FROM audit_chain_anchor"
                                + " ORDER BY purged_until_sequence DESC LIMIT 1",
                        (rows, index) ->
                                new AuditAnchor(
                                        rows.getLong("purged_until_sequence"),
                                        rows.getString("last_record_hash")))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AuditVerification> lastVerification() {
        return jdbc
                .query(
                        "SELECT occurred_at, result, item_count FROM authorship_record"
                                + " WHERE operation = 'AUDIT_TRAIL_VERIFY'"
                                + " ORDER BY sequence DESC LIMIT 1",
                        (rows, index) ->
                                new AuditVerification(
                                        rows.getTimestamp("occurred_at").toInstant(),
                                        "SUCCESS".equals(rows.getString("result")),
                                        rows.getLong("item_count")))
                .stream()
                .findFirst();
    }
}
