package br.com.felipeblaft.reports.usecase.job;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.service.TemporaryAudioService;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * "Descartar e começar um novo" para um registro em transcrição ou uma consolidação semestral ainda
 * em curso (sem relatório criado): o trabalho fica {@code DESCARTADO} e o resultado que o worker
 * produzir depois é ignorado. O áudio de um trabalho ainda pendente é apagado agora (FR-018); o de
 * um em execução, pelo próprio worker.
 */
public class DiscardRegistrationInProcessingUseCase {

    private final JobQueuePort queue;
    private final TemporaryAudioService audio;
    private final ClockPort clock;

    public DiscardRegistrationInProcessingUseCase(
            JobQueuePort queue, TemporaryAudioService audio, ClockPort clock) {
        this.queue = Objects.requireNonNull(queue);
        this.audio = Objects.requireNonNull(audio);
        this.clock = Objects.requireNonNull(clock);
    }

    public void run(UUID teacherId, UUID jobId) {
        ProcessingJob job =
                queue.searchById(jobId)
                        .filter(t -> t.teacherId().equals(teacherId))
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean registrationOrConsolidation =
                job.type() == JobType.LESSON_REGISTRATION
                        || job.type() == JobType.SEMESTER_CONSOLIDATION;
        if (!registrationOrConsolidation || job.reportId() != null) {
            throw new DomainException(ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
        }
        boolean pending = job.status() == JobStatus.PENDING;
        if (!job.discard(clock.now())) {
            throw new DomainException(ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
        }
        if (pending && job.temporaryFile() != null) {
            audio.discard(new TemporaryFile(Path.of(job.temporaryFile()), 0, null));
            job.defineTemporaryFile(null);
        }
        queue.update(job);
    }
}
