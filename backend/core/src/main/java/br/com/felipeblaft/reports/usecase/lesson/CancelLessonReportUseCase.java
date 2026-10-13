package br.com.felipeblaft.reports.usecase.lesson;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.service.ReportAccessService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancelar (ou descartar na retomada) um relatório em aberto (FR-031): deixa de aparecer para o
 * professor, fica registrado como cancelado e o resultado de um trabalho em curso é descartado.
 */
public class CancelLessonReportUseCase {

    private final ReportAccessService access;
    private final LessonReportRepository reports;
    private final JobQueuePort queue;
    private final ClockPort clock;

    public CancelLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            JobQueuePort queue,
            ClockPort clock) {
        this.access = Objects.requireNonNull(access);
        this.reports = Objects.requireNonNull(reports);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public void cancel(UUID teacherId, UUID reportId) {
        run(teacherId, reportId, false);
    }

    /** "Descartar e começar um novo" (retomada): também vale para relatório em processamento. */
    public void discard(UUID teacherId, UUID reportId) {
        run(teacherId, reportId, true);
    }

    private void run(UUID teacherId, UUID reportId, boolean discard) {
        LessonReport report = access.lessonReportForUpdate(teacherId, reportId);
        Instant now = clock.now();
        report.cancel(now, discard);
        reports.save(report);
        queue.searchInProgressByReport(ReportType.AULA, reportId)
                .ifPresent(
                        job -> {
                            job.discard(now);
                            queue.update(job);
                        });
    }
}
