package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportCriteria;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.port.ApprovedReportQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consulta única dos relatórios aprovados (feature 006, R-01): {@code UNION ALL} de {@code
 * lesson_report} (com a data da aula) e {@code semester_report}, sempre {@code status =
 * 'APPROVED'}, junto de {@code teacher} e {@code student} (só {@code id}, {@code name} e {@code
 * active}). A ordem é {@code reference_date DESC, approved_at DESC, report_id DESC}, a tradução em
 * SQL de {@code HistoryOrder.NEWEST_FIRST}. Os parâmetros são sempre ligados, nunca concatenados;
 * o único texto montado é a escolha de cláusulas fixas. Somente leitura.
 */
@Repository
class ApprovedReportQueryAdapter implements ApprovedReportQueryPort {

    private static final String ORDER =
            " ORDER BY a.reference_date DESC, a.approved_at DESC, a.report_id DESC";

    private final JdbcTemplate jdbc;

    ApprovedReportQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ramos da união com seus parâmetros, já filtrados pelo critério. */
    private record Union(String sql, List<Object> parameters) {}

    @Override
    public PageResult<ApprovedReportRow> page(
            ApprovedReportCriteria criteria, PageRequest request) {
        Union union = union(criteria);
        Long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM (" + union.sql() + ") a",
                        Long.class,
                        union.parameters().toArray());
        List<Object> parameters = new ArrayList<>(union.parameters());
        parameters.add(request.size());
        parameters.add(request.offset());
        List<ApprovedReportRow> items =
                jdbc.query(
                        select(union) + ORDER + " LIMIT ? OFFSET ?",
                        (rows, index) -> row(rows),
                        parameters.toArray());
        return new PageResult<>(items, request.page(), request.size(), total == null ? 0 : total);
    }

    @Override
    public List<ApprovedReportRow> all(ApprovedReportCriteria criteria) {
        Union union = union(criteria);
        return jdbc.query(
                select(union) + ORDER, (rows, index) -> row(rows), union.parameters().toArray());
    }

    private static String select(Union union) {
        return "SELECT a.report_type, a.report_id, a.student_id, a.teacher_id, a.lesson_date,"
                + " a.period_key, a.approved_at, a.approved_version, s.name AS student_name,"
                + " s.active AS student_active, t.name AS teacher_name, t.active AS teacher_active"
                + " FROM ("
                + union.sql()
                + ") a JOIN student s ON s.id = a.student_id"
                + " JOIN teacher t ON t.id = a.teacher_id";
    }

    private static Union union(ApprovedReportCriteria criteria) {
        List<Object> parameters = new ArrayList<>();
        List<String> branches = new ArrayList<>();
        if (criteria.type() == null || criteria.type() == ReportType.LESSON) {
            StringBuilder lesson =
                    new StringBuilder(
                            "SELECT 'LESSON' AS report_type, r.id AS report_id, r.student_id,"
                                    + " r.teacher_id, l.lesson_date AS lesson_date,"
                                    + " CAST(NULL AS varchar) AS period_key,"
                                    + " l.lesson_date AS reference_date, r.approved_at,"
                                    + " r.approved_version FROM lesson_report r"
                                    + " JOIN lesson l ON l.id = r.lesson_id"
                                    + " WHERE r.status = 'APPROVED'");
            filters(lesson, parameters, criteria, "r.student_id", "r.teacher_id", "l.lesson_date");
            branches.add(lesson.toString());
        }
        if (criteria.type() == null || criteria.type() == ReportType.SEMESTER) {
            StringBuilder semester =
                    new StringBuilder(
                            "SELECT 'SEMESTER' AS report_type, r.id AS report_id, r.student_id,"
                                    + " r.teacher_id, CAST(NULL AS date) AS lesson_date,"
                                    + " r.period_key AS period_key,"
                                    + " r.period_start AS reference_date, r.approved_at,"
                                    + " r.approved_version FROM semester_report r"
                                    + " WHERE r.status = 'APPROVED'");
            filters(
                    semester,
                    parameters,
                    criteria,
                    "r.student_id",
                    "r.teacher_id",
                    "r.period_start");
            branches.add(semester.toString());
        }
        return new Union(String.join(" UNION ALL ", branches), parameters);
    }

    /** Acrescenta as cláusulas fixas dos filtros presentes; os valores vão como parâmetros. */
    private static void filters(
            StringBuilder sql,
            List<Object> parameters,
            ApprovedReportCriteria criteria,
            String studentColumn,
            String teacherColumn,
            String referenceColumn) {
        if (criteria.studentId() != null) {
            sql.append(" AND ").append(studentColumn).append(" = ?");
            parameters.add(criteria.studentId());
        }
        if (criteria.teacherId() != null) {
            sql.append(" AND ").append(teacherColumn).append(" = ?");
            parameters.add(criteria.teacherId());
        }
        if (criteria.from() != null) {
            sql.append(" AND ").append(referenceColumn).append(" >= ?");
            parameters.add(criteria.from());
        }
        if (criteria.to() != null) {
            sql.append(" AND ").append(referenceColumn).append(" <= ?");
            parameters.add(criteria.to());
        }
    }

    private static ApprovedReportRow row(ResultSet rows) throws SQLException {
        ReportType type = ReportType.valueOf(rows.getString("report_type"));
        LocalDate lessonDate = rows.getObject("lesson_date", LocalDate.class);
        return new ApprovedReportRow(
                type,
                rows.getObject("report_id", UUID.class),
                rows.getObject("student_id", UUID.class),
                rows.getString("student_name"),
                rows.getBoolean("student_active"),
                rows.getObject("teacher_id", UUID.class),
                rows.getString("teacher_name"),
                rows.getBoolean("teacher_active"),
                type == ReportType.LESSON ? lessonDate : null,
                type == ReportType.SEMESTER ? SemesterPeriod.of(rows.getString("period_key")) : null,
                rows.getTimestamp("approved_at").toInstant(),
                rows.getInt("approved_version"));
    }
}
