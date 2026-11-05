package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** Entidades JPA do relatório de aula e do processamento (separadas do domínio, R-03). */
final class ReportEntities {

    private ReportEntities() {}

    @Entity(name = "LessonEntity")
    @Table(name = "lesson")
    static class LessonEntity {
        @Id UUID id;

        @Column(name = "teacher_id", nullable = false)
        UUID teacherId;

        @Column(name = "student_id", nullable = false)
        UUID studentId;

        @Column(name = "lesson_date", nullable = false)
        LocalDate lessonDate;

        @Column(name = "created_at", nullable = false)
        Instant createdAt;
    }

    @Entity(name = "LessonReportEntity")
    @Table(name = "lesson_report")
    static class LessonReportEntity {
        @Id UUID id;

        @Column(name = "lesson_id", nullable = false)
        UUID lessonId;

        @Column(name = "teacher_id", nullable = false)
        UUID teacherId;

        @Column(name = "student_id", nullable = false)
        UUID studentId;

        String transcription;

        @Column(name = "source_removed_at")
        Instant sourceRemovedAt;

        @Column(name = "anonymized_at")
        Instant anonymizedAt;

        @Column(nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String content;

        @Column(name = "pending_questions", nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String pendingQuestions;

        @Column(name = "question_rounds", nullable = false)
        int questionRounds;

        @Column(nullable = false)
        String status;

        @Column(name = "processing_step")
        String processingStep;

        @Column(name = "current_version", nullable = false)
        int currentVersion;

        @Column(name = "origin_channel", nullable = false)
        String originChannel;

        @Column(name = "failure_reason")
        String failureReason;

        @Column(name = "created_at", nullable = false)
        Instant createdAt;

        @Column(name = "updated_at", nullable = false)
        Instant updatedAt;

        @Column(name = "approved_at")
        Instant approvedAt;

        @Column(name = "approved_version")
        Integer approvedVersion;

        @Column(name = "approval_channel")
        String approvalChannel;

        @Column(name = "cancelled_at")
        Instant cancelledAt;

        @Column(name = "processing_time_ms", nullable = false)
        long processingTimeMs;

        @Version
        @Column(name = "lock_version", nullable = false)
        long lockVersion;
    }

    @Entity(name = "ReportVersionEntity")
    @Table(name = "report_version")
    static class ReportVersionEntity {
        @Id UUID id;

        @Column(name = "report_type", nullable = false)
        String reportType;

        @Column(name = "report_id", nullable = false)
        UUID reportId;

        @Column(nullable = false)
        int number;

        @Column(nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String content;

        @Column(nullable = false)
        String origin;

        @Column(name = "interaction_id")
        UUID interactionId;

        @Column(nullable = false)
        String channel;

        @Column(name = "prompt_version")
        String promptVersion;

        @Column(name = "content_removed_at")
        Instant contentRemovedAt;

        @Column(name = "created_at", nullable = false)
        Instant createdAt;
    }

    @Entity(name = "ReportInteractionEntity")
    @Table(name = "report_interaction")
    static class ReportInteractionEntity {
        @Id UUID id;

        @Column(name = "report_type", nullable = false)
        String reportType;

        @Column(name = "report_id", nullable = false)
        UUID reportId;

        @Column(nullable = false)
        String type;

        @Column(nullable = false)
        String channel;

        @Column(nullable = false)
        String medium;

        String text;

        @Column(name = "text_removed_at")
        Instant textRemovedAt;

        @Column(name = "base_version", nullable = false)
        int baseVersion;

        Integer round;

        @Column(nullable = false)
        String result;

        @Column(name = "resulting_version")
        Integer resultingVersion;

        @Column(name = "created_at", nullable = false)
        Instant createdAt;
    }

    @Entity(name = "PdfDocumentEntity")
    @Table(name = "pdf_document")
    static class PdfDocumentEntity {
        @Id UUID id;

        @Column(name = "version_id", nullable = false)
        UUID versionId;

        @Column(nullable = false)
        String type;

        @Column(name = "storage_key", nullable = false)
        String storageKey;

        @Column(name = "size_bytes", nullable = false)
        long sizeBytes;

        @Column(nullable = false)
        String sha256;

        @Column(name = "generated_at", nullable = false)
        Instant generatedAt;

        @Column(name = "content_removed_at")
        Instant contentRemovedAt;

        @Column(name = "file_removed_at")
        Instant fileRemovedAt;
    }

    @Entity(name = "ProcessingJobEntity")
    @Table(name = "processing_job")
    static class ProcessingJobEntity {
        @Id UUID id;

        @Column(nullable = false)
        String type;

        @Column(name = "teacher_id", nullable = false)
        UUID teacherId;

        @Column(name = "student_id", nullable = false)
        UUID studentId;

        @Column(name = "report_type")
        String reportType;

        @Column(name = "report_id")
        UUID reportId;

        @Column(name = "request_channel", nullable = false)
        String requestChannel;

        @Column(nullable = false)
        String medium;

        @Column(nullable = false, columnDefinition = "jsonb")
        @ColumnTransformer(write = "?::jsonb")
        String parameters;

        @Column(name = "temporary_file")
        String temporaryFile;

        @Column(nullable = false)
        String status;

        @Column(nullable = false)
        String step;

        @Column(name = "result_code")
        String resultCode;

        @Column(name = "resulting_report_id")
        UUID resultingReportId;

        @Column(nullable = false)
        int attempts;

        @Column(name = "delay_notice_sent", nullable = false)
        boolean delayNoticeSent;

        @Column(name = "created_at", nullable = false)
        Instant createdAt;

        @Column(name = "started_at")
        Instant startedAt;

        @Column(name = "completed_at")
        Instant completedAt;
    }

    @Entity(name = "AiCallEntity")
    @Table(name = "ai_call")
    static class AiCallEntity {
        @Id UUID id;

        @Column(name = "job_id", nullable = false)
        UUID jobId;

        @Column(nullable = false)
        String channel;

        @Column(name = "report_type")
        String reportType;

        @Column(name = "report_id")
        UUID reportId;

        @Column(nullable = false)
        String task;

        @Column(name = "provider_model")
        String providerModel;

        @Column(name = "audio_seconds")
        BigDecimal audioSeconds;

        @Column(name = "input_tokens")
        Integer inputTokens;

        @Column(name = "output_tokens")
        Integer outputTokens;

        @Column(name = "duration_ms", nullable = false)
        long durationMs;

        @Column(name = "estimated_cost_usd")
        BigDecimal estimatedCostUsd;

        @Column(nullable = false)
        boolean success;

        @Column(name = "error_code")
        String errorCode;

        @Column(name = "created_at", nullable = false)
        Instant createdAt;
    }
}
