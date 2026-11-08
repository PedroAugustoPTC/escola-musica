package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.port.CostReportQueryPort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Chamadas de IA de um período, com o aluno do relatório quando ele ainda existe (FR-053). A junção
 * traz só o identificador; o nome vem de {@link #studentNames} e é só para exibição.
 */
@Repository
class CostReportQueryAdapter implements CostReportQueryPort {

    private final JdbcTemplate jdbc;

    CostReportQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, String> studentNames(Collection<UUID> studentIds) {
        Map<UUID, String> names = new HashMap<>();
        if (studentIds.isEmpty()) {
            return names;
        }
        String marks = String.join(",", java.util.Collections.nCopies(studentIds.size(), "?"));
        jdbc.query(
                "SELECT id, name FROM student WHERE id IN (" + marks + ")",
                rs -> {
                    names.put(rs.getObject("id", UUID.class), rs.getString("name"));
                },
                studentIds.toArray());
        return names;
    }

    @Override
    public List<CostLine> lines(Instant from, Instant to) {
        return jdbc.query(
                "SELECT c.report_type, c.report_id, COALESCE(lr.student_id, sr.student_id) AS"
                        + " student_id, c.task, c.channel, c.provider_model,"
                        + " COALESCE(c.estimated_cost_usd, 0) AS cost, c.duration_ms, c.success"
                        + " FROM ai_call c"
                        + " LEFT JOIN lesson_report lr ON c.report_type = 'LESSON' AND lr.id ="
                        + " c.report_id"
                        + " LEFT JOIN semester_report sr ON c.report_type = 'SEMESTER' AND sr.id ="
                        + " c.report_id"
                        + " WHERE c.created_at >= ? AND c.created_at < ?",
                (rs, row) -> {
                    String type = rs.getString("report_type");
                    return new CostLine(
                            type == null ? null : ReportType.valueOf(type),
                            rs.getObject("report_id", UUID.class),
                            rs.getObject("student_id", UUID.class),
                            AiTask.valueOf(rs.getString("task")),
                            Channel.valueOf(rs.getString("channel")),
                            rs.getString("provider_model"),
                            rs.getObject("cost", BigDecimal.class),
                            rs.getLong("duration_ms"),
                            rs.getBoolean("success"));
                },
                Timestamp.from(from),
                Timestamp.from(to));
    }
}
