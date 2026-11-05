package br.com.felipeblaft.reports.domain.report;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.privacy.RetentionDecision;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatório de aula e sua máquina de estados (data-model.md §8.1). Toda mudança de conteúdo após a
 * primeira conferência gera nova versão; a aprovação exige a versão vigente e nenhuma etapa em
 * curso (Princípio III).
 */
public class LessonReport {

    /** No máximo duas rodadas de perguntas por relatório (FR-025). */
    public static final int MAX_ROUNDS = 2;

    public static final String STRUCTURING_FAILED = "STRUCTURING_FAILED";

    private final UUID id;
    private final UUID lessonId;
    private final UUID teacherId;
    private final UUID studentId;
    private String transcription;
    private Instant sourceRemovedAt;
    private Instant anonymizedAt;
    private LessonReportContent content;
    private List<String> pendingQuestions;
    private int questionRounds;
    private ReportStatus status;
    private ReportStep step;
    private int currentVersion;
    private final Channel originChannel;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant approvedAt;
    private Integer approvedVersion;
    private Channel approvalChannel;
    private Instant cancelledAt;
    private long processingTimeMs;
    private long version;

    @SuppressWarnings("java:S107")
    public LessonReport(
            UUID id,
            UUID lessonId,
            UUID teacherId,
            UUID studentId,
            String transcription,
            LessonReportContent content,
            List<String> pendingQuestions,
            int questionRounds,
            ReportStatus status,
            ReportStep step,
            int currentVersion,
            Channel originChannel,
            String failureReason,
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
                lessonId,
                teacherId,
                studentId,
                Objects.requireNonNull(transcription),
                content,
                pendingQuestions,
                questionRounds,
                status,
                step,
                currentVersion,
                originChannel,
                failureReason,
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

    /** Construtor de reidratação: a fonte pode já ter sido removida pela retenção (FR-010). */
    @SuppressWarnings("java:S107")
    public LessonReport(
            UUID id,
            UUID lessonId,
            UUID teacherId,
            UUID studentId,
            String transcription,
            LessonReportContent content,
            List<String> pendingQuestions,
            int questionRounds,
            ReportStatus status,
            ReportStep step,
            int currentVersion,
            Channel originChannel,
            String failureReason,
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
        this.lessonId = Objects.requireNonNull(lessonId);
        this.teacherId = Objects.requireNonNull(teacherId);
        this.studentId = Objects.requireNonNull(studentId);
        this.transcription = transcription;
        this.sourceRemovedAt = sourceRemovedAt;
        this.anonymizedAt = anonymizedAt;
        if (transcription == null && sourceRemovedAt == null && anonymizedAt == null) {
            throw new IllegalArgumentException("transcrição ausente exige remoção registrada");
        }
        this.content = content == null ? LessonReportContent.empty() : content;
        this.pendingQuestions =
                pendingQuestions == null ? List.of() : List.copyOf(pendingQuestions);
        this.questionRounds = questionRounds;
        this.status = Objects.requireNonNull(status);
        this.step = step;
        this.currentVersion = currentVersion;
        this.originChannel = Objects.requireNonNull(originChannel);
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.approvedAt = approvedAt;
        this.approvedVersion = approvedVersion;
        this.approvalChannel = approvalChannel;
        this.cancelledAt = cancelledAt;
        this.processingTimeMs = processingTimeMs;
        this.version = version;
    }

    /** Criado só depois de a transcrição dar certo (FR-017), já estruturando. */
    public static LessonReport create(
            Lesson lesson, String transcription, Channel channel, Instant now) {
        if (transcription == null || transcription.isBlank()) {
            throw new IllegalArgumentException("transcrição vazia não cria relatório");
        }
        return new LessonReport(
                GeneratorId.create(),
                lesson.id(),
                lesson.teacherId(),
                lesson.studentId(),
                transcription.strip(),
                LessonReportContent.empty(),
                List.of(),
                0,
                ReportStatus.PROCESSING,
                ReportStep.STRUCTURING,
                0,
                channel,
                null,
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

    /** Recusa qualquer ação sobre relatório aprovado ou cancelado (FR-031, FR-032). */
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

    private void requireStatus(ReportStatus expected) {
        if (status != expected) {
            throw new DomainException(ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
        }
    }

    public void requireCanAnswer() {
        requireOpen();
        requireNoStepInProgress();
        requireStatus(ReportStatus.AWAITING_ANSWERS);
    }

    /** Pedir alteração exige conferência sem etapa em curso e a versão vigente (FR-033). */
    public void requireCanChange(int baseVersion) {
        requireOpen();
        requireNoStepInProgress();
        requireStatus(ReportStatus.AWAITING_REVIEW);
        requireCurrentVersion(baseVersion);
    }

    public void requireCurrentVersion(int presentedVersion) {
        if (presentedVersion != currentVersion) {
            throw new DomainException(
                    ErrorCode.STALE_VERSION, Map.of("currentVersion", currentVersion));
        }
    }

    public void requireCanRetry() {
        requireOpen();
        requireNoStepInProgress();
        requireStatus(ReportStatus.PROCESSING_FAILED);
    }

    // ------------------------------------------------------------ transições

    /**
     * Resultado da estruturação. Com perguntas (e rodada disponível) aguarda respostas; senão vai
     * para conferência na versão 1. Devolve {@code true} se a versão 1 deve ser criada.
     */
    public boolean registerStructuring(
            LessonReportContent newContent, List<String> questions, Instant now) {
        requireInternal(ReportStatus.PROCESSING);
        return applyContentWithQuestions(newContent, questions, now);
    }

    private boolean applyContentWithQuestions(
            LessonReportContent newContent, List<String> questions, Instant now) {
        content = Objects.requireNonNull(newContent);
        List<String> valid = LessonReportContent.items(questions);
        step = null;
        failureReason = null;
        updatedAt = now;
        if (!valid.isEmpty() && questionRounds < MAX_ROUNDS) {
            questionRounds++;
            pendingQuestions = valid;
            status = ReportStatus.AWAITING_ANSWERS;
            return false;
        }
        pendingQuestions = List.of();
        status = ReportStatus.AWAITING_REVIEW;
        currentVersion = 1;
        return true;
    }

    public void failStructuring(Instant now) {
        requireInternal(ReportStatus.PROCESSING);
        status = ReportStatus.PROCESSING_FAILED;
        failureReason = STRUCTURING_FAILED;
        step = null;
        updatedAt = now;
    }

    public void prepareNewAttempt(Instant now) {
        requireCanRetry();
        status = ReportStatus.PROCESSING;
        step = ReportStep.STRUCTURING;
        failureReason = null;
        updatedAt = now;
    }

    public void startStep(ReportStep newStep, Instant now) {
        step = Objects.requireNonNull(newStep);
        updatedAt = now;
    }

    public void completeStep(Instant now) {
        step = null;
        updatedAt = now;
    }

    /**
     * Resposta incorporada (FR-025): se ainda houver perguntas e rodada disponível, nova rodada;
     * senão, conferência na versão 1. Devolve {@code true} se a versão 1 deve ser criada.
     */
    public boolean incorporateAnswer(
            LessonReportContent newContent, List<String> newQuestions, Instant now) {
        requireInternal(ReportStatus.AWAITING_ANSWERS);
        return applyContentWithQuestions(newContent, newQuestions, now);
    }

    /** Alteração aplicada: nova versão (N+1). Devolve o número da nova versão. */
    public int applyChange(LessonReportContent newContent, Instant now) {
        requireInternal(ReportStatus.AWAITING_REVIEW);
        content = Objects.requireNonNull(newContent);
        currentVersion++;
        step = null;
        updatedAt = now;
        return currentVersion;
    }

    /** Aprovação explícita da versão apresentada (FR-028 a FR-030). */
    public void approve(int confirmedVersion, Channel channel, Instant now) {
        requireOpen();
        requireNoStepInProgress();
        requireStatus(ReportStatus.AWAITING_REVIEW);
        requireCurrentVersion(confirmedVersion);
        status = ReportStatus.APPROVED;
        approvedAt = now;
        approvedVersion = currentVersion;
        approvalChannel = Objects.requireNonNull(channel);
        updatedAt = now;
    }

    /**
     * Cancelamento (FR-031). Em processamento, só é permitido pelo descarte na retomada; o
     * resultado de um trabalho em curso é descartado.
     */
    public void cancel(Instant now, boolean discardOnResume) {
        requireOpen();
        if (status == ReportStatus.PROCESSING && !discardOnResume) {
            throw new DomainException(ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
        }
        status = ReportStatus.CANCELLED;
        cancelledAt = now;
        step = null;
        updatedAt = now;
    }

    /**
     * Decisão da retenção (FR-010 a FR-013): o aprovado perde a fonte após o prazo da transcrição; o
     * cancelado é anonimizado após o prazo próprio; relatório em aberto nunca é tocado.
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

    /** Remove a fonte (transcrição); o conteúdo das versões fica (FR-010). */
    public void removeSource(Instant now) {
        transcription = null;
        if (sourceRemovedAt == null) {
            sourceRemovedAt = now;
        }
    }

    /** Anonimiza o cancelado vencido: fonte removida e marca de anonimização (FR-013). */
    public void anonymize(Instant now) {
        removeSource(now);
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

    private void requireInternal(ReportStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("transição inválida a partir de " + status);
        }
    }

    public void registerPersistedVersion(long savedVersion) {
        this.version = savedVersion;
    }

    // ------------------------------------------------------------ leitura

    public UUID id() {
        return id;
    }

    public UUID lessonId() {
        return lessonId;
    }

    public UUID teacherId() {
        return teacherId;
    }

    public UUID studentId() {
        return studentId;
    }

    public String transcription() {
        return transcription;
    }

    public LessonReportContent content() {
        return content;
    }

    public List<String> pendingQuestions() {
        return pendingQuestions;
    }

    public int questionRounds() {
        return questionRounds;
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

    public String failureReason() {
        return failureReason;
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
        return "LessonReport[id=" + id + ", status=" + status + ", version=" + currentVersion + "]";
    }
}
