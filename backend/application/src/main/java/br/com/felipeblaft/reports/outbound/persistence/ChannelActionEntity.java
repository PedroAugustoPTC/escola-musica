package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Linha da outbox (data-model.md §1.2). A entidade só serve à leitura: as gravações são SQL nativo
 * condicional ({@link ChannelActionJpa}), porque a concorrência é controlada pela coluna {@code
 * versao} e não pelo contexto de persistência.
 */
@Entity
@Table(name = "channel_action")
public class ChannelActionEntity {

    @Id String id;

    @Column(name = "seq", insertable = false, updatable = false)
    long seq;

    @Column(name = "batch_id", nullable = false)
    String batchId;

    @Column(nullable = false)
    String channel;

    @Column(nullable = false)
    String origin;

    @Column(name = "update_id")
    Long updateId;

    @Column(name = "chat_id", nullable = false)
    long chatId;

    @Column(name = "teacher_id")
    UUID teacherId;

    @Column(name = "job_id")
    UUID jobId;

    @Column(nullable = false)
    String type;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String payload;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String compensation;

    @Column(nullable = false)
    String state;

    @Column(nullable = false)
    int attempts;

    @Column(name = "next_attempt_at")
    Instant nextAttemptAt;

    @Column(name = "deadline_at", nullable = false)
    Instant deadlineAt;

    @Column(nullable = false)
    int version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "delivered_at")
    Instant deliveredAt;

    @Column(name = "finalized_at")
    Instant finalizedAt;

    @Column(name = "reason_code")
    String reasonCode;

    protected ChannelActionEntity() {}
}
