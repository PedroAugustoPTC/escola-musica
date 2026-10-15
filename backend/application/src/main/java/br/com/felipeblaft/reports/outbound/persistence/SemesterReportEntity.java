package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "semester_report")
class SemesterReportEntity {
    @Id UUID id;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "student_id", nullable = false)
    UUID studentId;

    @Column(name = "period_key", nullable = false)
    String periodKey;

    @Column(name = "period_start", nullable = false)
    LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    LocalDate periodEnd;

    @Column(name = "considered_lesson_count", nullable = false)
    int consideredLessonCount;

    @Column(name = "open_report_count", nullable = false)
    int openReportCount;

    @Column(name = "general_information", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String generalInformation;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String content;

    @Column(nullable = false)
    String status;

    @Column(name = "processing_step")
    String processingStep;

    @Column(name = "current_version", nullable = false)
    int currentVersion;

    @Column(name = "origin_channel", nullable = false)
    String originChannel;

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
