package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fila de trabalhos persistida (R-06). */
public interface JobQueuePort {

    /**
     * @throws ConcurrencyConflictException se já houver registro de aula em curso para o par
     *     professor–aluno (índice único parcial)
     */
    void enqueue(ProcessingJob job);

    void update(ProcessingJob job);

    Optional<ProcessingJob> searchById(UUID id);

    /**
     * Reserva o próximo trabalho pendente (sem disputar com outros workers) e o marca em execução.
     */
    Optional<ProcessingJob> reserveNext(Instant now);

    Optional<ProcessingJob> searchInProgressByReport(ReportType type, UUID reportId);

    /** Registro de aula (ou consolidação) ainda em curso para o aluno, sem relatório criado. */
    Optional<ProcessingJob> searchRegistrationInProgress(UUID teacherId, UUID studentId);

    /**
     * Marca o aviso de demora só se ainda não foi marcado; devolve {@code true} se marcou agora.
     */
    boolean markDelayNotice(UUID jobId);

    /**
     * Grava o id da mensagem de etapa do canal no trabalho, sem disputar com a gravação do worker;
     * as etapas seguintes editam essa mensagem (FR-017).
     */
    void registerStepMessage(UUID jobId, long messageId);

    /** Consolidação semestral ainda em curso para o trio professor–aluno–período (FR-034). */
    Optional<ProcessingJob> searchConsolidationInProgress(
            UUID teacherId, UUID studentId, String periodKey);

    /** Trabalhos em curso criados antes do limite e ainda sem aviso de demora. */
    List<ProcessingJob> listDelayedWithoutNotice(Instant createdBefore);
}
