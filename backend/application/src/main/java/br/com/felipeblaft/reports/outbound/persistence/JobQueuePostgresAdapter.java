package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.ProcessingJobEntity;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * Fila de trabalhos no PostgreSQL (R-06): reserva com {@code FOR UPDATE SKIP LOCKED}, sem duplicar
 * trabalho entre workers; índice único parcial impede dois registros em curso para o mesmo aluno.
 */
@Repository
public class JobQueuePostgresAdapter implements JobQueuePort {

    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {};

    private final ProcessingJobJpa jpa;

    JobQueuePostgresAdapter(ProcessingJobJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void enqueue(ProcessingJob job) {
        try {
            jpa.saveAndFlush(toEntity(job));
        } catch (DataIntegrityViolationException conflict) {
            throw new ConcurrencyConflictException("processing_job");
        }
    }

    @Override
    @Transactional
    public void update(ProcessingJob job) {
        // um trabalho descartado por outro canal não volta a ficar ativo
        Optional<ProcessingJobEntity> current = jpa.findById(job.id());
        if (current.isPresent()
                && JobStatus.DISCARDED.name().equals(current.get().status)
                && job.status() != JobStatus.DISCARDED) {
            return;
        }
        // a mensagem de etapa do Telegram é registrada à parte, pelo envio; a gravação do worker a
        // mantém
        if (current.isPresent() && job.parameter(ProcessingJob.STEP_MESSAGE).isEmpty()) {
            JsonPersistence.read(current.get().parameters, MAP).entrySet().stream()
                    .filter(p -> ProcessingJob.STEP_MESSAGE.equals(p.getKey()))
                    .findFirst()
                    .ifPresent(p -> job.defineParameter(p.getKey(), p.getValue()));
        }
        ProcessingJobEntity entity = toEntity(job);
        // o aviso de demora é marcado à parte; uma gravação do worker não o desfaz
        entity.delayNoticeSent =
                entity.delayNoticeSent || current.map(a -> a.delayNoticeSent).orElse(false);
        jpa.saveAndFlush(entity);
    }

    /** Grava o id da mensagem de etapa do Telegram sem disputar com a gravação do worker. */
    @Override
    @Transactional
    public void registerStepMessage(UUID jobId, long messageId) {
        jpa.defineParameter(jobId, ProcessingJob.STEP_MESSAGE, Long.toString(messageId));
    }

    @Override
    @Transactional
    public boolean markDelayNotice(UUID jobId) {
        return jpa.markDelayNotice(jobId) > 0;
    }

    @Override
    public Optional<ProcessingJob> searchById(UUID id) {
        return jpa.findById(id).map(JobQueuePostgresAdapter::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ProcessingJob> reserveNext(Instant now) {
        Optional<UUID> id = jpa.nextPending();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        ProcessingJob job = toDomain(jpa.findById(id.get()).orElseThrow());
        job.start(now);
        jpa.saveAndFlush(toEntity(job));
        return Optional.of(job);
    }

    @Override
    public Optional<ProcessingJob> searchInProgressByReport(ReportType type, UUID reportId) {
        return jpa.reportInProgress(type.name(), reportId).stream()
                .findFirst()
                .map(JobQueuePostgresAdapter::toDomain);
    }

    @Override
    public Optional<ProcessingJob> searchRegistrationInProgress(UUID teacherId, UUID studentId) {
        return jpa.registrationInProgress(teacherId, studentId).stream()
                .findFirst()
                .map(JobQueuePostgresAdapter::toDomain);
    }

    @Override
    public List<ProcessingJob> listDelayedWithoutNotice(Instant createdBefore) {
        return jpa.delayedWithoutNotice(createdBefore).stream()
                .map(JobQueuePostgresAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<ProcessingJob> searchConsolidationInProgress(
            UUID teacherId, UUID studentId, String periodKey) {
        return jpa.consolidationInProgress(teacherId, studentId, periodKey).stream()
                .findFirst()
                .map(JobQueuePostgresAdapter::toDomain);
    }

    /**
     * Consolidações semestrais em curso do professor (semestrais em aberto ainda sem relatório).
     */
    List<ProcessingJob> teacherConsolidationsInProgress(UUID teacherId) {
        return jpa.teacherConsolidationsInProgress(teacherId).stream()
                .map(JobQueuePostgresAdapter::toDomain)
                .toList();
    }

    /** Registros de aula em transcrição do professor (relatórios em aberto ainda sem relatório). */
    List<ProcessingJob> teacherRegistrationsInProgress(UUID teacherId) {
        return jpa.teacherRegistrationsInProgress(teacherId).stream()
                .map(JobQueuePostgresAdapter::toDomain)
                .toList();
    }

    /**
     * Trabalhos que estavam em execução quando o backend caiu: voltam para a fila, exceto os que
     * dependiam de um arquivo do diretório temporário (apagado na reinicialização), que falham.
     */
    @Transactional
    public int recoverStuck(Instant now) {
        int recovered = 0;
        for (ProcessingJobEntity entity : jpa.findByStatus(JobStatus.RUNNING.name())) {
            ProcessingJob job = toDomain(entity);
            if (job.temporaryFile() != null) {
                job.complete(JobResult.AUDIO_NOT_PROCESSABLE, now);
                job.defineTemporaryFile(null);
            } else {
                job.returnToQueue();
            }
            jpa.save(toEntity(job));
            recovered++;
        }
        return recovered;
    }

    static ProcessingJobEntity toEntity(ProcessingJob t) {
        ProcessingJobEntity e = new ProcessingJobEntity();
        e.id = t.id();
        e.type = t.type().name();
        e.teacherId = t.teacherId();
        e.studentId = t.studentId();
        e.reportType = t.reportType() == null ? null : t.reportType().name();
        e.reportId = t.reportId();
        e.requestChannel = t.requestChannel().name();
        e.medium = t.medium().name();
        e.parameters = JsonPersistence.write(t.parameters());
        e.temporaryFile = t.temporaryFile();
        e.status = t.status().name();
        e.step = t.step().name();
        e.resultCode = t.result() == null ? null : t.result().name();
        e.resultingReportId = t.resultingReportId();
        e.attempts = t.attempts();
        e.delayNoticeSent = t.delayNoticeSent();
        e.createdAt = t.createdAt();
        e.startedAt = t.startedAt();
        e.completedAt = t.completedAt();
        return e;
    }

    static ProcessingJob toDomain(ProcessingJobEntity e) {
        return new ProcessingJob(
                e.id,
                JobType.valueOf(e.type),
                e.teacherId,
                e.studentId,
                e.reportType == null ? null : ReportType.valueOf(e.reportType),
                e.reportId,
                Channel.valueOf(e.requestChannel),
                Medium.valueOf(e.medium),
                JsonPersistence.read(e.parameters, MAP),
                e.temporaryFile,
                JobStatus.valueOf(e.status),
                JobStep.valueOf(e.step),
                e.resultCode == null ? null : JobResult.valueOf(e.resultCode),
                e.resultingReportId,
                e.attempts,
                e.delayNoticeSent,
                e.createdAt,
                e.startedAt,
                e.completedAt);
    }
}
