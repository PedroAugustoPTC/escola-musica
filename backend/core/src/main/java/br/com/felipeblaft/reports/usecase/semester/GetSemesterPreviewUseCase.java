package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.AuthorizationService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Prévia do semestral (FR-042): quantas aulas aprovadas e quantos relatórios de aula em aberto há
 * no período, e se já existe semestral aprovado ou em aberto (incluindo consolidação em curso).
 */
public class GetSemesterPreviewUseCase {

    public record Preview(
            SemesterPeriod period,
            int approvedLessons,
            int openLessonReports,
            Optional<UUID> approvedSemesterId,
            Optional<OpenReport> openSemester) {}

    private final AuthorizationService authorization;
    private final StudentRepository students;
    private final LessonReportRepository lessons;
    private final SemesterReportRepository semester;
    private final JobQueuePort queue;

    public GetSemesterPreviewUseCase(
            AuthorizationService authorization,
            StudentRepository students,
            LessonReportRepository lessons,
            SemesterReportRepository semester,
            JobQueuePort queue) {
        this.authorization = Objects.requireNonNull(authorization);
        this.students = Objects.requireNonNull(students);
        this.lessons = Objects.requireNonNull(lessons);
        this.semester = Objects.requireNonNull(semester);
        this.queue = Objects.requireNonNull(queue);
    }

    public Preview run(UUID teacherId, UUID studentId, String periodKey) {
        authorization.requireAssignment(teacherId, studentId);
        SemesterPeriod period = SemesterPeriod.of(periodKey);
        int approvedLessons =
                lessons.listApprovedInPeriod(teacherId, studentId, period.start(), period.end())
                        .size();
        int open = lessons.countOpenInPeriod(teacherId, studentId, period.start(), period.end());
        Optional<UUID> approved =
                semester.searchApproved(teacherId, studentId, period.key()).map(SemesterReport::id);
        return new Preview(
                period,
                approvedLessons,
                open,
                approved,
                openSemester(teacherId, studentId, period));
    }

    /** Semestral em aberto do trio, ou consolidação ainda em curso (FR-034). */
    Optional<OpenReport> openSemester(UUID teacherId, UUID studentId, SemesterPeriod period) {
        String name = students.searchById(studentId).map(a -> a.name()).orElse("");
        Optional<OpenReport> report =
                semester.searchOpen(teacherId, studentId, period.key())
                        .map(
                                r ->
                                        new OpenReport(
                                                ReportType.SEMESTER,
                                                r.id(),
                                                null,
                                                studentId,
                                                name,
                                                r.status(),
                                                null,
                                                period.key(),
                                                r.originChannel(),
                                                r.updatedAt()));
        if (report.isPresent()) {
            return report;
        }
        Optional<ProcessingJob> consolidation =
                queue.searchConsolidationInProgress(teacherId, studentId, period.key());
        return consolidation.map(
                t ->
                        new OpenReport(
                                ReportType.SEMESTER,
                                null,
                                t.id(),
                                studentId,
                                name,
                                null,
                                t.step(),
                                period.key(),
                                t.requestChannel(),
                                t.createdAt()));
    }
}
