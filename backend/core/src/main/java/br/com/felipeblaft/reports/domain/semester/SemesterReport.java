package br.com.felipeblaft.reports.domain.semester;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.privacy.RetentionDecision;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatório semestral (data-model.md §9). Nasce em conferência na versão 1, só depois de a
 * consolidação dar certo (FR-045); alterar, aprovar e cancelar seguem as regras do relatório de
 * aula (Princípio III).
 */
public class SemesterReport {

    private final UUID id;
    private final UUID teacherId;
    private final UUID studentId;
    private final SemesterPeriod period;
    private final int consideredLessonCount;
    private final int openReportCount;
    private GeneralInformation generalInformation;
    private Instant sourceRemovedAt;
    private Instant anonymizedAt;
    private SemesterReportContent content;
    private ReportStatus status;
    private ReportStep step;
    private int currentVersion;
    private final Channel originChannel;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant approvedAt;
    private Integer approvedVersion;
    private Channel approvalChannel;
    private Instant cancelledAt;
    private long processingTimeMs;
    private long version;

    @SuppressWarnings("java:S107")
    public SemesterReport(
            UUID id,
            UUID teacherId,
            UUID studentId,
            SemesterPeriod period,
            int consideredLessonCount,
            int openReportCount,
            GeneralInformation generalInformation,
            SemesterReportContent content,
            ReportStatus status,
            ReportStep step,
            int currentVersion,
            Channel originChannel,
            Instant createdAt,
            Instant updatedAt,
            Instant approvedAt,
            Integer approvedVersion,
            Channel approvalChannel,
            Instant cancelledAt,
            long processingTimeMs,
            long version) {
        this(
                id,
                teacherId,
                studentId,
                period,
                consideredLessonCount,
                openReportCount,
                Objects.requireNonNull(generalInformation),
                content,
                status,
                step,
                currentVersion,
                originChannel,
                createdAt,
                updatedAt,
                approvedAt,
                approvedVersion,
                approvalChannel,
                cancelledAt,
                processingTimeMs,
                version,
                null,
                null);
    }

    /** Reidratação: {@code generalInformation} só falta com {@code anonymizedAt} (CHECK do banco). */
    @SuppressWarnings("java:S107")
    public SemesterReport(
            UUID id,
            UUID teacherId,
            UUID studentId,
            SemesterPeriod period,
            int consideredLessonCount,
            int openReportCount,
            GeneralInformation generalInformation,
            SemesterReportContent content,
            ReportStatus status,
            ReportStep step,
            int currentVersion,
            Channel originChannel,
            Instant createdAt,
            Instant updatedAt,
            Instant approvedAt,
            Integer approvedVersion,
            Channel approvalChannel,
            Instant cancelledAt,
            long processingTimeMs,
            long version,
            Instant sourceRemovedAt,
            Instant anonymizedAt) {
        this.id = Objects.requireNonNull(id);
        this.teacherId = Objects.requireNonNull(teacherId);
        this.studentId = Objects.requireNonNull(studentId);
        this.period = Objects.requireNonNull(period);
        if (consideredLessonCount < 1) {
            throw new IllegalArgumentException("o semestral considera ao menos uma aula aprovada");
        }
        this.consideredLessonCount = consideredLessonCount;
        this.openReportCount = Math.max(0, openReportCount);
        if (generalInformation == null && anonymizedAt == null) {
            throw new IllegalArgumentException("informações gerais só faltam após anonimização");
        }
        this.generalInformation = generalInformation;
        this.sourceRemovedAt = sourceRemovedAt;
        this.anonymizedAt = anonymizedAt;
        this.content = content == null ? SemesterReportContent.empty() : content;
        this.status = Objects.requireNonNull(status);
        this.step = step;
        this.currentVersion = currentVersion;
        this.originChannel = Objects.requireNonNull(originChannel);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.approvedAt = approvedAt;
        this.approvedVersion = approvedVersion;
        this.approvalChannel = approvalChannel;
        this.cancelledAt = cancelledAt;
        this.processingTimeMs = processingTimeMs;
        this.version = version;
    }

    /** Consolidação bem-sucedida: conferência na versão 1. */
    @SuppressWarnings("java:S107")
    public static SemesterReport create(
            UUID teacherId,
            UUID studentId,
            SemesterPeriod period,
            int lessonCount,
            int openCount,
            GeneralInformation generalInformation,
            SemesterReportContent content,
            Channel channel,
            Instant now) {
        return new SemesterReport(
                GeneratorId.create(),
                teacherId,
                studentId,
                period,
                lessonCount,
                openCount,
                generalInformation,
                content,
                ReportStatus.AWAITING_REVIEW,
                null,
                1,
                channel,
                now,
                now,
                null,
                null,
                null,
                null,
                0,
                0);
    }

    // ------------------------------------------------------------ verificações

    public void requireOpen() {
        if (status == ReportStatus.APPROVED) {
            throw new DomainException(ErrorCode.REPORT_ALREADY_APPROVED);
        }
        if (status == ReportStatus.CANCELLED) {
            throw new DomainException(ErrorCode.REPORT_CANCELLED);
        }
    }

    private void requireNoStepInProgress() {
        if (step != null) {
            throw new DomainException(
                    ErrorCode.PROCESSING_IN_PROGRESS, Map.of("step", step.name()));
        }
    }

    public void requireCurrentVersion(int presentedVersion) {
        if (presentedVersion != currentVersion) {
            throw new DomainException(
                    ErrorCode.STALE_VERSION, Map.of("currentVersion", currentVersion));
        }
    }

    public void requireCanChange(int baseVersion) {
        requireOpen();
        requireNoStepInProgress();
        requireCurrentVersion(baseVersion);
    }

    // ------------------------------------------------------------ transições

    public void startStep(ReportStep newStep, Instant now) {
        step = Objects.requireNonNull(newStep);
        updatedAt = now;
    }

    public void completeStep(Instant now) {
        step = null;
        updatedAt = now;
    }

    /** Alteração aplicada: nova versão (N+1). Devolve o número da nova versão. */
    public int applyChange(SemesterReportContent newContent, Instant now) {
        if (status != ReportStatus.AWAITING_REVIEW) {
            throw new IllegalStateException("transição inválida a partir de " + status);
        }
        content = Objects.requireNonNull(newContent);
        currentVersion++;
        step = null;
        updatedAt = now;
        return currentVersion;
    }

    public void approve(int confirmedVersion, Channel channel, Instant now) {
        requireOpen();
        requireNoStepInProgress();
        requireCurrentVersion(confirmedVersion);
        status = ReportStatus.APPROVED;
        approvedAt = now;
        approvedVersion = currentVersion;
        approvalChannel = Objects.requireNonNull(channel);
        updatedAt = now;
    }

    public void cancel(Instant now) {
        requireOpen();
        status = ReportStatus.CANCELLED;
        cancelledAt = now;
        step = null;
        updatedAt = now;
    }

    /**
     * Decisão da retenção. O semestral aprovado perde só o texto das interações; as informações
     * gerais fazem parte do documento oficial e nunca saem (research R-01).
     */
    public RetentionDecision retentionAction(RetentionPolicy policy, Instant now) {
        if (anonymizedAt != null) {
            return RetentionDecision.KEEP;
        }
        if (status == ReportStatus.CANCELLED
                && RetentionDecision.expired(
                        cancelledAt, policy.cancelledReportDays().value(), now)) {
            return RetentionDecision.ANONYMIZE;
        }
        if (status == ReportStatus.APPROVED
                && sourceRemovedAt == null
                && RetentionDecision.expired(
                        approvedAt, policy.transcriptionDays().value(), now)) {
            return RetentionDecision.REMOVE_SOURCE;
        }
        return RetentionDecision.KEEP;
    }

    /** Remove o texto das interações; {@code generalInformation} não é tocada. */
    public void removeSource(Instant now) {
        if (sourceRemovedAt == null) {
            sourceRemovedAt = now;
        }
    }

    /** Só o cancelado vencido (ou a eliminação) anula as informações gerais. */
    public void anonymize(Instant now) {
        removeSource(now);
        generalInformation = null;
        anonymizedAt = now;
    }

    public Instant sourceRemovedAt() {
        return sourceRemovedAt;
    }

    public Instant anonymizedAt() {
        return anonymizedAt;
    }

    public void addProcessingTime(long milliseconds) {
        processingTimeMs += Math.max(0, milliseconds);
    }

    public void registerPersistedVersion(long savedVersion) {
        this.version = savedVersion;
    }

    // ------------------------------------------------------------ leitura

    public UUID id() {
        return id;
    }

    public UUID teacherId() {
        return teacherId;
    }

    public UUID studentId() {
        return studentId;
    }

    public SemesterPeriod period() {
        return period;
    }

    public int consideredLessonCount() {
        return consideredLessonCount;
    }

    public int openReportCount() {
        return openReportCount;
    }

    public GeneralInformation generalInformation() {
        return generalInformation;
    }

    public SemesterReportContent content() {
        return content;
    }

    public ReportStatus status() {
        return status;
    }

    public ReportStep step() {
        return step;
    }

    public int currentVersion() {
        return currentVersion;
    }

    public Channel originChannel() {
        return originChannel;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant approvedAt() {
        return approvedAt;
    }

    public Integer approvedVersion() {
        return approvedVersion;
    }

    public Channel approvalChannel() {
        return approvalChannel;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public long processingTimeMs() {
        return processingTimeMs;
    }

    public long version() {
        return version;
    }

    @Override
    public String toString() {
        return "SemesterReport[id=" + id + ", period=" + period.key() + ", status=" + status + "]";
    }
}
