package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Identificador opaco de PDF para o orquestrador (data-model.md §1.3). */
@Entity
@Table(name = "channel_document")
public class ChannelDocumentEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, columnDefinition = "char(64)")
    String tokenHash;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "report_type", nullable = false)
    String reportType;

    @Column(name = "report_id", nullable = false)
    UUID reportId;

    Integer version;

    @Column(name = "file_name", nullable = false)
    String fileName;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected ChannelDocumentEntity() {}
}
