package br.com.felipeblaft.reports.usecase.lesson;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.port.AudioInput;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.service.AudioSendingService;
import br.com.felipeblaft.reports.service.ReportAccessService;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedir alteração em linguagem natural, por texto ou áudio (FR-027, FR-027a, FR-033): exige
 * conferência, nenhuma etapa em curso e a versão vigente; nunca aprova nem cancela.
 */
public class RequestLessonReportChangeUseCase {

    private final ReportAccessService access;
    private final LessonReportRepository reports;
    private final AudioSendingService audioSending;
    private final JobQueuePort queue;
    private final ClockPort clock;

    public RequestLessonReportChangeUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock) {
        this.access = Objects.requireNonNull(access);
        this.reports = Objects.requireNonNull(reports);
        this.audioSending = Objects.requireNonNull(audioSending);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProcessingJob changeByText(
            UUID teacherId, UUID reportId, String text, int baseVersion, Channel channel) {
        LessonReport report = access.lessonReportForUpdate(teacherId, reportId);
        report.requireCanChange(baseVersion);
        if (text == null || text.isBlank()) {
            throw new DomainException(ErrorCode.ALTERACAO_VAZIA);
        }
        ProcessingJob job =
                start(report, channel, Medium.TEXTO, ReportStep.APLICANDO_ALTERACAO, baseVersion);
        job.defineParameter(ProcessingJob.TEXT, text.strip());
        queue.enqueue(job);
        return job;
    }

    public ProcessingJob changeByAudio(
            UUID teacherId, UUID reportId, AudioInput audio, int baseVersion, Channel channel) {
        try {
            LessonReport report = access.lessonReportForUpdate(teacherId, reportId);
            report.requireCanChange(baseVersion);
            audioSending.requireWithinLimit(audio);
            ProcessingJob job =
                    start(
                            report,
                            channel,
                            Medium.AUDIO,
                            ReportStep.TRANSCREVENDO_PEDIDO,
                            baseVersion);
            audioSending.attach(job, audio);
            queue.enqueue(job);
            return job;
        } catch (DomainException refuses) {
            audioSending.discard(audio);
            throw refuses;
        }
    }

    private ProcessingJob start(
            LessonReport report, Channel channel, Medium medium, ReportStep step, int baseVersion) {
        report.startStep(step, clock.now());
        reports.save(report);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.ALTERACAO,
                        report.teacherId(),
                        report.studentId(),
                        ReportType.AULA,
                        report.id(),
                        channel,
                        medium,
                        medium == Medium.AUDIO
                                ? JobStep.TRANSCREVENDO_PEDIDO
                                : JobStep.APLICANDO_ALTERACAO,
                        clock.now());
        job.defineParameter(ProcessingJob.BASE_VERSION, Integer.toString(baseVersion));
        return job;
    }
}
