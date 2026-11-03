package br.com.felipeblaft.reports.usecase.job;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.service.AuthorizationService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Etapa e resultado de um processamento, consultados pela Web a cada 2 s (FR-021). Trabalho de
 * outro professor, ou de aluno que já não é do professor, é {@code RESOURCE_NOT_FOUND}.
 */
public class QueryJobUseCase {

    public record JobView(ProcessingJob job, boolean delayed, Optional<String> understoodText) {}

    private final AuthorizationService authorization;
    private final JobQueuePort queue;
    private final ClockPort clock;

    public QueryJobUseCase(
            AuthorizationService authorization, JobQueuePort queue, ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public JobView run(UUID teacherId, UUID jobId) {
        ProcessingJob job =
                queue.searchById(jobId)
                        .filter(t -> t.teacherId().equals(teacherId))
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        // o acesso ao trabalho acaba junto com a associação ao aluno (FR-050)
        authorization.requireAssignment(teacherId, job.studentId());
        Optional<String> text =
                job.parameter(ProcessingJob.UNDERSTOOD_TEXT)
                        .or(() -> job.parameter(ProcessingJob.TEXT));
        return new JobView(job, job.delayed(clock.now()), text);
    }
}
