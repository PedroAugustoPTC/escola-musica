package br.com.felipeblaft.reports.usecase.lesson;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.port.AudioInput;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.service.AudioSendingService;
import br.com.felipeblaft.reports.service.AuthorizationService;
import br.com.felipeblaft.reports.service.ReportEligibilityService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Registrar uma aula por áudio (FR-014 a FR-017, FR-034): exige associação, nenhum relatório em
 * aberto para o aluno e o limite de tamanho; enfileira o processamento com o canal de origem. O
 * áudio de um envio recusado é descartado.
 */
public class RegisterLessonByAudioUseCase {

    private final AuthorizationService authorization;
    private final OpenReportsQueryPort open;
    private final AudioSendingService audioSending;
    private final JobQueuePort queue;
    private final ClockPort clock;
    private final ReportEligibilityService eligibility;

    /**
     * Sem verificação de situação do professor e do aluno: só para os cenários da 001, que ainda não
     * conhecem a situação. A montagem de produção usa o construtor completo.
     */
    public RegisterLessonByAudioUseCase(
            AuthorizationService authorization,
            OpenReportsQueryPort open,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock) {
        this(authorization, open, audioSending, queue, clock, null);
    }

    public RegisterLessonByAudioUseCase(
            AuthorizationService authorization,
            OpenReportsQueryPort open,
            AudioSendingService audioSending,
            JobQueuePort queue,
            ClockPort clock,
            ReportEligibilityService eligibility) {
        this.eligibility = eligibility;
        this.authorization = Objects.requireNonNull(authorization);
        this.open = Objects.requireNonNull(open);
        this.audioSending = Objects.requireNonNull(audioSending);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProcessingJob run(UUID teacherId, UUID studentId, AudioInput audio, Channel channel) {
        try {
            authorization.requireAssignment(teacherId, studentId);
            requireNoOpenReport(teacherId, studentId);
            audioSending.requireWithinLimit(audio);
            ProcessingJob job =
                    ProcessingJob.create(
                            JobType.LESSON_REGISTRATION,
                            teacherId,
                            studentId,
                            null,
                            null,
                            channel,
                            Medium.AUDIO,
                            JobStep.UPLOADING,
                            clock.now());
            audioSending.attach(job, audio);
            try {
                queue.enqueue(job);
            } catch (ConcurrencyConflictException conflict) {
                requireNoOpenReport(teacherId, studentId);
                throw new DomainException(ErrorCode.REPORT_ALREADY_OPEN);
            }
            return job;
        } catch (DomainException refuses) {
            audioSending.discard(audio);
            throw refuses;
        }
    }

    private void requireNoOpenReport(UUID teacherId, UUID studentId) {
        Optional<OpenReport> openReport = open.searchByStudent(teacherId, studentId);
        if (openReport.isPresent()) {
            throw new DomainException(
                    ErrorCode.REPORT_ALREADY_OPEN,
                    Map.of("openReport", openReport.get().details()));
        }
    }
}
