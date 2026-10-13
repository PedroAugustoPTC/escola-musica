package br.com.felipeblaft.reports.usecase.semester;

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

    public CancelSemesterReportUseCase(
            SemesterAccessService access,
            SemesterReportRepository semester,
            JobQueuePort queue,
            ClockPort clock) {
        this.access = Objects.requireNonNull(access);
        this.semester = Objects.requireNonNull(semester);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public void cancel(UUID teacherId, UUID reportId) {
        SemesterReport report = access.reportForUpdate(teacherId, reportId);
        Instant now = clock.now();
        report.cancel(now);
        semester.save(report);
        queue.searchInProgressByReport(ReportType.SEMESTRAL, reportId)
                .ifPresent(
                        job -> {
                            job.discard(now);
                            queue.update(job);
                        });
    }

    /** Descarta uma consolidação ainda em curso (sem semestral criado). */
    public void discardConsolidation(UUID teacherId, UUID jobId) {
        ProcessingJob job =
                queue.searchById(jobId)
                        .filter(t -> t.teacherId().equals(teacherId))
                        .filter(t -> t.type() == JobType.CONSOLIDACAO_SEMESTRAL)
                        .orElseThrow(() -> new DomainException(ErrorCode.RECURSO_NAO_ENCONTRADO));
        if (!job.discard(clock.now())) {
            throw new DomainException(ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        queue.update(job);
    }
}
