package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.AudioInput;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.service.AudioSendingService;
import br.com.felipeblaft.reports.service.SemesterAccessService;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedido de alteração do semestral em linguagem natural, por texto ou áudio (FR-043, FR-033): como
 * no relatório de aula, exige a versão vigente e gera nova versão; nunca altera as Informações
 * Gerais.
 */
public class RequestSemesterReportChangeUseCase {

    private final SemesterAccessService access;
    private final SemesterReportRepository semester;
    private final AudioSendingService audioSending;
    private final JobQueuePort queue;
    private final ClockPort clock;

    public RequestSemesterReportChangeUseCase(
            SemesterAccessService access,
            SemesterReportRepository semester,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock) {
        this.access = Objects.requireNonNull(access);
        this.semester = Objects.requireNonNull(semester);
        this.audioSending = Objects.requireNonNull(audioSending);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProcessingJob changeByText(
            UUID teacherId, UUID reportId, String text, int baseVersion, Channel channel) {
        SemesterReport report = access.reportForUpdate(teacherId, reportId);
        report.requireCanChange(baseVersion);
        if (text == null || text.isBlank()) {
            throw new DomainException(ErrorCode.EMPTY_CHANGE);
        }
        ProcessingJob job = start(report, channel, Medium.TEXT, baseVersion);
        job.defineParameter(ProcessingJob.TEXT, text.strip());
        queue.enqueue(job);
        return job;
    }

    public ProcessingJob changeByAudio(
            UUID teacherId, UUID reportId, AudioInput audio, int baseVersion, Channel channel) {
        try {
            SemesterReport report = access.reportForUpdate(teacherId, reportId);
            report.requireCanChange(baseVersion);
            audioSending.requireWithinLimit(audio);
            ProcessingJob job = start(report, channel, Medium.AUDIO, baseVersion);
            audioSending.attach(job, audio);
            queue.enqueue(job);
            return job;
        } catch (DomainException refuses) {
            audioSending.discard(audio);
            throw refuses;
        }
    }

    private ProcessingJob start(
            SemesterReport report, Channel channel, Medium medium, int baseVersion) {
        report.startStep(
                medium == Medium.AUDIO
                        ? ReportStep.TRANSCRIBING_REQUEST
                        : ReportStep.APPLYING_CHANGE,
                clock.now());
        semester.save(report);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.CHANGE,
                        report.teacherId(),
                        report.studentId(),
                        ReportType.SEMESTER,
                        report.id(),
                        channel,
                        medium,
                        medium == Medium.AUDIO
                                ? JobStep.TRANSCRIBING_REQUEST
                                : JobStep.APPLYING_CHANGE,
                        clock.now());
        job.defineParameter(ProcessingJob.BASE_VERSION, Integer.toString(baseVersion));
        return job;
    }
}
