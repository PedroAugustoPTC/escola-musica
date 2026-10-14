package br.com.felipeblaft.reports.domain.job;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Trabalho da fila persistida (data-model.md §13, R-06): fonte das etapas mostradas nos dois canais
 * e do resultado entregue no canal em que foi pedido (FR-021).
 */
public class ProcessingJob {

    /** Depois de 3 minutos sem conclusão, os canais avisam que está demorando. */
    public static final Duration DELAY_LIMIT = Duration.ofMinutes(3);

    public static final int MAX_ATTEMPTS = 2;

    // chaves conhecidas dos parâmetros
    public static final String TEXT = "text";
    public static final String BASE_VERSION = "baseVersion";
    public static final String UNDERSTOOD_TEXT = "understoodText";
    public static final String CHANNEL_FILE = "channelFile";
    public static final String UNIQUE_CHANNEL_FILE = "uniqueChannelFile";
    public static final String FILE_MIME = "fileMime";
    public static final String FILE_SIZE = "fileSize";
    public static final String STEP_MESSAGE = "stepMessage";
    public static final String PERIOD_KEY = "periodKey";
    public static final String CONFIRM_OPEN = "confirmEvenWithOpen";

    private final UUID id;
    private final JobType type;
    private final UUID teacherId;
    private final UUID studentId;
    private ReportType reportType;
    private UUID reportId;
    private final Channel requestChannel;
    private final Medium medium;
    private final Map<String, String> parameters;
    private String temporaryFile;
    private JobStatus status;
    private JobStep step;
    private JobResult result;
    private UUID resultingReportId;
    private int attempts;
    private boolean delayNoticeSent;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    @SuppressWarnings("java:S107")
    public ProcessingJob(
            UUID id,
            JobType type,
            UUID teacherId,
            UUID studentId,
            ReportType reportType,
            UUID reportId,
            Channel requestChannel,
            Medium medium,
            Map<String, String> parameters,
            String temporaryFile,
            JobStatus status,
            JobStep step,
            JobResult result,
            UUID resultingReportId,
            int attempts,
            boolean delayNoticeSent,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.teacherId = Objects.requireNonNull(teacherId);
        this.studentId = Objects.requireNonNull(studentId);
        this.reportType = reportType;
        this.reportId = reportId;
        this.requestChannel = Objects.requireNonNull(requestChannel);
        this.medium = Objects.requireNonNull(medium);
        this.parameters = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);
        this.temporaryFile = temporaryFile;
        this.status = Objects.requireNonNull(status);
        this.step = Objects.requireNonNull(step);
        this.result = result;
        this.resultingReportId = resultingReportId;
        this.attempts = attempts;
        this.delayNoticeSent = delayNoticeSent;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public static ProcessingJob create(
            JobType type,
            UUID teacherId,
            UUID studentId,
            ReportType reportType,
            UUID reportId,
            Channel channel,
            Medium medium,
            JobStep initialStep,
            Instant now) {
        return new ProcessingJob(
                GeneratorId.create(),
                type,
                teacherId,
                studentId,
                reportType,
                reportId,
                channel,
                medium,
                Map.of(),
                null,
                JobStatus.PENDING,
                initialStep,
                null,
                null,
                0,
                false,
                now,
                null,
                null);
    }

    public void defineParameter(String key, String value) {
        if (value == null) {
            parameters.remove(key);
        } else {
            parameters.put(key, value);
        }
    }

    public Optional<String> parameter(String key) {
        return Optional.ofNullable(parameters.get(key));
    }

    public void defineTemporaryFile(String path) {
        this.temporaryFile = path;
    }

    public void start(Instant now) {
        if (status != JobStatus.PENDING) {
            throw new IllegalStateException("trabalho não está pendente");
        }
        status = JobStatus.RUNNING;
        attempts++;
        if (startedAt == null) {
            startedAt = now;
        }
    }

    public void advance(JobStep newStep) {
        step = Objects.requireNonNull(newStep);
    }

    public void linkReport(ReportType type, UUID report) {
        this.reportType = type;
        this.reportId = report;
        this.resultingReportId = report;
    }

    public void complete(JobResult finalResult, Instant now) {
        status = finalResult.success() ? JobStatus.COMPLETED : JobStatus.FAILED;
        if (finalResult == JobResult.DISCARDED) {
            status = JobStatus.DISCARDED;
        }
        result = finalResult;
        if (finalResult.success()) {
            step = JobStep.READY;
        }
        completedAt = now;
    }

    /** Descarte pela retomada ("descartar e começar um novo"): o resultado posterior é ignorado. */
    public boolean discard(Instant now) {
        if (!status.inProgress()) {
            return false;
        }
        status = JobStatus.DISCARDED;
        result = JobResult.DISCARDED;
        completedAt = now;
        return true;
    }

    /** Volta à fila (trabalho travado recuperado após reinício). */
    public void returnToQueue() {
        if (status == JobStatus.RUNNING) {
            status = JobStatus.PENDING;
        }
    }

    public boolean delayed(Instant now) {
        return status.inProgress() && !now.isBefore(createdAt.plus(DELAY_LIMIT));
    }

    public void markDelayNotice() {
        this.delayNoticeSent = true;
    }

    public long durationMs() {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return Duration.between(startedAt, completedAt).toMillis();
    }

    public UUID id() {
        return id;
    }

    public JobType type() {
        return type;
    }

    public UUID teacherId() {
        return teacherId;
    }

    public UUID studentId() {
        return studentId;
    }

    public ReportType reportType() {
        return reportType;
    }

    public UUID reportId() {
        return reportId;
    }

    public Channel requestChannel() {
        return requestChannel;
    }

    public Medium medium() {
        return medium;
    }

    public Map<String, String> parameters() {
        return Map.copyOf(parameters);
    }

    public String temporaryFile() {
        return temporaryFile;
    }

    public JobStatus status() {
        return status;
    }

    public JobStep step() {
        return step;
    }

    public JobResult result() {
        return result;
    }

    public UUID resultingReportId() {
        return resultingReportId;
    }

    public int attempts() {
        return attempts;
    }

    public boolean delayNoticeSent() {
        return delayNoticeSent;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    @Override
    public String toString() {
        return "ProcessingJob[id=" + id + ", type=" + type + ", status=" + status + "]";
    }
}
