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
import br.com.felipeblaft.reports.service.ConsentGuardService;
import br.com.felipeblaft.reports.service.AudioSendingService;
import br.com.felipeblaft.reports.service.ReportAccessService;
import java.util.Objects;
import java.util.UUID;

/**
 * Responder às perguntas pendentes numa única mensagem, por texto ou áudio (FR-024, FR-024a). A
 * resposta é incorporada pela IA no processamento (FR-025).
 */
public class AnswerQuestionsUseCase {

    private ConsentGuardService consent;
    private final ReportAccessService access;
    private final LessonReportRepository reports;
    private final AudioSendingService audioSending;
    private final JobQueuePort queue;
    private final ClockPort clock;

    public AnswerQuestionsUseCase(
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

    public ProcessingJob answerText(UUID teacherId, UUID reportId, String text, Channel channel) {
        LessonReport report = access.lessonReportForUpdate(teacherId, reportId);
        report.requireCanAnswer();
        requireConsent(teacherId, report.studentId());
        if (text == null || text.isBlank()) {
            throw new DomainException(ErrorCode.EMPTY_ANSWER);
        }
        ProcessingJob job = start(report, channel, Medium.TEXT, ReportStep.INCORPORATING_ANSWERS);
        job.defineParameter(ProcessingJob.TEXT, text.strip());
        queue.enqueue(job);
        return job;
    }

    public ProcessingJob answerAudio(
            UUID teacherId, UUID reportId, AudioInput audio, Channel channel) {
        try {
            LessonReport report = access.lessonReportForUpdate(teacherId, reportId);
            report.requireCanAnswer();
            requireConsent(teacherId, report.studentId());
            audioSending.requireWithinLimit(audio);
            ProcessingJob job =
                    start(report, channel, Medium.AUDIO, ReportStep.TRANSCRIBING_REQUEST);
            audioSending.attach(job, audio);
            queue.enqueue(job);
            return job;
        } catch (DomainException refuses) {
            audioSending.discard(audio);
            throw refuses;
        }
    }

    private ProcessingJob start(
            LessonReport report, Channel channel, Medium medium, ReportStep step) {
        report.startStep(step, clock.now());
        reports.save(report);
        return ProcessingJob.create(
                JobType.QUESTION_ANSWER,
                report.teacherId(),
                report.studentId(),
                ReportType.LESSON,
                report.id(),
                channel,
                medium,
                medium == Medium.AUDIO
                        ? JobStep.TRANSCRIBING_REQUEST
                        : JobStep.INCORPORATING_ANSWERS,
                clock.now());
    }

    /**
     * Liga a barreira de consentimento (FR-034): aluno menor sem consentimento válido não chega a
     * nenhum provedor de IA. A montagem de produção a liga; cenários anteriores à feature 007
     * seguem sem ela.
     */
    public void useConsentGuard(ConsentGuardService guard) {
        this.consent = guard;
    }

    private void requireConsent(UUID teacherId, UUID studentId) {
        if (consent != null) {
            consent.requireForAi(teacherId, studentId);
        }
    }
}
