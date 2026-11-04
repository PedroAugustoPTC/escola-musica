package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.audit.AuditEventType;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.service.SemesterAccessService;
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancelar o semestral em aberto (FR-031) ou descartar uma consolidação ainda em curso (retomada):
 * o resultado posterior do trabalho é ignorado.
 */
public class CancelSemesterReportUseCase {

    private final SemesterAccessService access;
    private final SemesterReportRepository semester;
    private final JobQueuePort queue;
    private final ClockPort clock;
    private final TeacherReportAuditService audit;

    public CancelSemesterReportUseCase(
            SemesterAccessService access,
            SemesterReportRepository semester,
            JobQueuePort queue,
            ClockPort clock,
            TeacherReportAuditService audit) {
        this.access = Objects.requireNonNull(access);
        this.semester = Objects.requireNonNull(semester);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
        this.audit = Objects.requireNonNull(audit);
    }

    public void cancel(UUID teacherId, UUID reportId) {
        SemesterReport report = access.reportForUpdate(teacherId, reportId);
        Instant now = clock.now();
        report.cancel(now);
        semester.save(report);
        queue.searchInProgressByReport(ReportType.SEMESTER, reportId)
                .ifPresent(
                        job -> {
                            job.discard(now);
                            queue.update(job);
                        });
        audit.reportDecided(
                teacherId,
                AuditEventType.SEMESTER_REPORT_CANCEL,
                ReportType.SEMESTER,
                reportId,
                null);
    }

    /** Descarta uma consolidação ainda em curso (sem semestral criado). */
    public void discardConsolidation(UUID teacherId, UUID jobId) {
        ProcessingJob job =
                queue.searchById(jobId)
                        .filter(t -> t.teacherId().equals(teacherId))
                        .filter(t -> t.type() == JobType.SEMESTER_CONSOLIDATION)
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.discard(clock.now())) {
            throw new DomainException(ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
        }
        queue.update(job);
    }
}
