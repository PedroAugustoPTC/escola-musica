package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.domain.common.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Linha de {@code authorship_record} (V10). Só é inserida: {@link #isNew()} é sempre verdadeiro, de
 * modo que o Spring Data nunca faz {@code merge} (que viraria {@code UPDATE}, barrado pelo gatilho
 * de somente acréscimo).
 */
@Entity
@Table(name = "authorship_record")
public class AuthorshipRecordEntity implements Persistable<UUID> {

    @Id private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "author_id")
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role")
    private Role authorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionOrigin origin;

    @Column(nullable = false)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private AuthorshipTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthorshipResult result;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "origin_hash")
    private String originHash;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    // detalhe das leituras da visão da escola (V16): só identificadores, tipo, datas e números
    @Column(name = "filter_teacher_id")
    private UUID filterTeacherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "filter_report_type")
    private ReportType filterReportType;

    @Column(name = "filter_period_start")
    private LocalDate filterPeriodStart;

    @Column(name = "filter_period_end")
    private LocalDate filterPeriodEnd;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "document_version")
    private Integer documentVersion;

    // trilha de auditoria (V17): filtros da consulta da trilha e quantidade de itens tratados
    @Column(name = "filter_operation")
    private String filterOperation;

    @Column(name = "filter_actor_id")
    private UUID filterActorId;

    @Column(name = "filter_target_id")
    private UUID filterTargetId;

    @Column(name = "item_count")
    private Integer itemCount;

    // trilha encadeada (V17): sequência, hash do evento anterior e hash do próprio evento
    @Column(name = "sequence", nullable = false)
    private long sequence;

    @Column(name = "previous_hash", nullable = false)
    private String previousHash;

    @Column(name = "record_hash", nullable = false)
    private String recordHash;

    @Transient private boolean inserted = true;

    protected AuthorshipRecordEntity() {}

    AuthorshipRecordEntity(AuthorshipRecord record) {
        this.id = record.id();
        // o banco guarda microssegundos (arredonda); o hash da cadeia usa o valor já truncado
        this.occurredAt = record.occurredAt().truncatedTo(ChronoUnit.MICROS);
        this.authorId = record.authorId();
        this.authorRole = record.authorRole();
        this.origin = record.origin();
        this.operation = record.operation();
        this.targetType = record.targetType();
        this.targetId = record.targetId();
        this.result = record.result();
        this.errorCode = record.errorCode();
        this.originHash = record.originHash();
        this.correlationId = record.correlationId();
        AuthorshipReadDetail detail = record.readDetail();
        if (detail != null) {
            this.filterTeacherId = detail.filterTeacherId();
            this.filterReportType = detail.filterReportType();
            this.filterPeriodStart = detail.filterPeriodStart();
            this.filterPeriodEnd = detail.filterPeriodEnd();
            this.pageNumber = detail.pageNumber();
            this.documentVersion = detail.documentVersion();
            this.filterOperation = detail.filterOperation();
            this.filterActorId = detail.filterActorId();
            this.filterTargetId = detail.filterTargetId();
            this.itemCount = detail.itemCount();
        }
    }

    /** Campos que entram no hash do evento, na ordem da forma canônica do núcleo. */
    AuditChain.Fields chainFields(long sequenceNumber) {
        return new AuditChain.Fields(
                sequenceNumber,
                occurredAt,
                authorId,
                authorRole == null ? null : authorRole.name(),
                origin.name(),
                operation,
                targetType == null ? null : targetType.name(),
                targetId,
                result.name(),
                errorCode,
                originHash,
                correlationId,
                filterTeacherId,
                filterReportType == null ? null : filterReportType.name(),
                filterPeriodStart,
                filterPeriodEnd,
                filterOperation,
                filterActorId,
                filterTargetId,
                pageNumber,
                documentVersion,
                itemCount);
    }

    void chain(long sequenceNumber, String previous, String hash) {
        this.sequence = sequenceNumber;
        this.previousHash = previous;
        this.recordHash = hash;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return inserted;
    }
}
