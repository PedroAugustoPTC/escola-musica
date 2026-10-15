package br.com.felipeblaft.reports.usecase.lesson;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.service.ReportAccessService;
import java.util.Objects;
import java.util.UUID;

/** Tentar de novo a estruturação sem reenviar o áudio: a transcrição está guardada (FR-022). */
public class RetryLessonReportUseCase {

    private final ReportAccessService access;
    private final LessonReportRepository reports;
    private final JobQueuePort queue;
    private final ClockPort clock;

    public RetryLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            JobQueuePort queue,
            ClockPort clock) {
        this.access = Objects.requireNonNull(access);
        this.reports = Objects.requireNonNull(reports);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProcessingJob run(UUID teacherId, UUID reportId, Channel channel) {
        LessonReport report = access.lessonReportForUpdate(teacherId, reportId);
        report.prepareNewAttempt(clock.now());
        reports.save(report);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.RETRY,
                        teacherId,
                        report.studentId(),
                        ReportType.LESSON,
                        report.id(),
                        channel,
                        Medium.TEXT,
                        JobStep.STRUCTURING,
                        clock.now());
        queue.enqueue(job);
        return job;
    }
}
