package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "telegram_link")
public class TelegramLinkEntity {

    @Id UUID id;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "telegram_user_id", nullable = false)
    long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    long chatId;

    @Column(name = "linked_at", nullable = false)
    Instant linkedAt;

    @Column(name = "selected_student_id")
    UUID selectedStudentId;

    @Column(nullable = false)
    String flow;

    @Column(name = "flow_step", nullable = false)
    String flowStep;

    @Column(name = "flow_data", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String flowData;

    @Column(name = "pending_audio_file_id")
    String pendingAudioFileId;

    @Column(name = "pending_audio_file_unique_id")
    String pendingAudioFileUniqueId;

    @Column(name = "pending_audio_mime")
    String pendingAudioMime;

    @Column(name = "pending_audio_size")
    Long pendingAudioSize;

    @Column(name = "pending_audio_received_at")
    Instant pendingAudioReceivedAt;

    @Column(name = "focused_report_type")
    String focusedReportType;

    @Column(name = "focused_report_id")
    UUID focusedReportId;

    @Column(name = "presented_version")
    Integer presentedVersion;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    long lockVersion;

    protected TelegramLinkEntity() {}
}
