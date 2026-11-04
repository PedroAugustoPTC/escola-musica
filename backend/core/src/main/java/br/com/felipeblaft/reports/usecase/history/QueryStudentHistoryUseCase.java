package br.com.felipeblaft.reports.usecase.history;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportCriteria;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.port.ApprovedReportQueryPort;
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
import br.com.felipeblaft.reports.usecase.home.GetStudentSummaryUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Histórico do aluno (FR-047): relatórios de aula e semestrais aprovados do professor, do mais
 * recente ao mais antigo — aula pela data da aula, semestral pelo início do período, empate pela
 * aprovação. Igual nos dois canais. Usa a mesma consulta e a mesma ordem ({@link
 * br.com.felipeblaft.reports.domain.history.HistoryOrder}) da visão da escola (feature 006).
 */
public class QueryStudentHistoryUseCase {

    /** Item do histórico; {@code dataAula} só na aula e {@code periodo} só no semestral. */
    public record HistoryItem(
            ReportType type,
            UUID reportId,
            LocalDate lessonDate,
            SemesterPeriod period,
            Instant approvedAt) {}

    public record History(StudentSummary student, List<HistoryItem> items) {}

    private final GetStudentSummaryUseCase studentSummary;
    private final ApprovedReportQueryPort approved;
    private final TeacherReportAuditService audit;

    public QueryStudentHistoryUseCase(
            GetStudentSummaryUseCase studentSummary,
            ApprovedReportQueryPort approved,
            TeacherReportAuditService audit) {
        this.studentSummary = Objects.requireNonNull(studentSummary);
        this.approved = Objects.requireNonNull(approved);
        this.audit = Objects.requireNonNull(audit);
    }

    public History run(UUID teacherId, UUID studentId) {
        StudentSummary student = studentSummary.run(teacherId, studentId);
        List<HistoryItem> items =
                approved.all(ApprovedReportCriteria.ofTeacherStudent(teacherId, studentId)).stream()
                        .map(QueryStudentHistoryUseCase::item)
                        .toList();
        audit.historyQueried(teacherId, studentId);
        return new History(student, items);
    }

    private static HistoryItem item(ApprovedReportRow row) {
        return new HistoryItem(
                row.type(), row.reportId(), row.lessonDate(), row.period(), row.approvedAt());
    }
}
