package br.com.felipeblaft.reports.domain.audit;

import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Encadeamento da trilha de auditoria (FR-040 a FR-046): cada evento guarda o hash do anterior e o
 * próprio, de modo que alterar, remover ou reordenar uma linha quebra a verificação.
 *
 * <p>A forma canônica tem ordem fixa, separa os campos por {@code |}, grava ausente como texto
 * vazio e escreve a data em ISO-8601 UTC com microssegundos. A migração {@code V17} calcula os
 * hashes das linhas antigas com a mesma forma; um teste de integração compara os dois.
 */
public final class AuditChain {

    /** Hash que antecede o primeiro evento da trilha (nenhuma âncora). */
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private AuditChain() {}

    /** Campos que entram no hash, na ordem da forma canônica. */
    public record Fields(
            long sequence,
            Instant occurredAt,
            UUID authorId,
            String authorRole,
            String origin,
            String operation,
            String targetType,
            UUID targetId,
            String result,
            String errorCode,
            String originHash,
            String correlationId,
            UUID filterTeacherId,
            String filterReportType,
            LocalDate filterPeriodStart,
            LocalDate filterPeriodEnd,
            String filterOperation,
            UUID filterActorId,
            UUID filterTargetId,
            Integer pageNumber,
            Integer documentVersion,
            Integer itemCount) {}

    /** Um evento já encadeado, como lido da trilha. */
    public record Link(Fields fields, String previousHash, String recordHash) {}

    /**
     * Os campos de hash de um registro de autoria, com a data truncada em microssegundos (a
     * precisão do banco), de modo que o hash calculado na gravação é o mesmo da verificação.
     */
    public static Fields fieldsOf(AuthorshipRecord record, long sequence) {
        AuthorshipReadDetail detail = record.readDetail();
        return new Fields(
                sequence,
                record.occurredAt().truncatedTo(ChronoUnit.MICROS),
                record.authorId(),
                record.authorRole() == null ? null : record.authorRole().name(),
                record.origin().name(),
                record.operation(),
                record.targetType() == null ? null : record.targetType().name(),
                record.targetId(),
                record.result().name(),
                record.errorCode(),
                record.originHash(),
                record.correlationId(),
                detail == null ? null : detail.filterTeacherId(),
                detail == null || detail.filterReportType() == null
                        ? null
                        : detail.filterReportType().name(),
                detail == null ? null : detail.filterPeriodStart(),
                detail == null ? null : detail.filterPeriodEnd(),
                detail == null ? null : detail.filterOperation(),
                detail == null ? null : detail.filterActorId(),
                detail == null ? null : detail.filterTargetId(),
                detail == null ? null : detail.pageNumber(),
                detail == null ? null : detail.documentVersion(),
                detail == null ? null : detail.itemCount());
    }

    public static String canonical(Fields f) {
        StringBuilder text = new StringBuilder();
        append(text, Long.toString(f.sequence()));
        append(text, TIMESTAMP.format(f.occurredAt()));
        append(text, f.authorId());
        append(text, f.authorRole());
        append(text, f.origin());
        append(text, f.operation());
        append(text, f.targetType());
        append(text, f.targetId());
        append(text, f.result());
        append(text, f.errorCode());
        append(text, f.originHash());
        append(text, f.correlationId());
        append(text, f.filterTeacherId());
        append(text, f.filterReportType());
        append(text, f.filterPeriodStart());
        append(text, f.filterPeriodEnd());
        append(text, f.filterOperation());
        append(text, f.filterActorId());
        append(text, f.filterTargetId());
        append(text, f.pageNumber());
        append(text, f.documentVersion());
        text.append(f.itemCount() == null ? "" : f.itemCount().toString());
        return text.toString();
    }

    private static void append(StringBuilder text, Object value) {
        text.append(value == null ? "" : value.toString()).append('|');
    }

    public static String hash(String previousHash, Fields fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes =
                    digest.digest(
                            (previousHash + "|" + canonical(fields))
                                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Primeira sequência cuja linha não confere, ou vazio se a cadeia está íntegra. {@code
     * startHash} é o hash da âncora (ou {@link #GENESIS_HASH}); as linhas vêm em ordem de
     * sequência.
     */
    public static OptionalLong firstBroken(List<Link> links, String startHash) {
        String expectedPrevious = startHash;
        for (Link link : links) {
            boolean linked = expectedPrevious.equals(link.previousHash());
            boolean intact =
                    linked && hash(link.previousHash(), link.fields()).equals(link.recordHash());
            if (!intact) {
                return OptionalLong.of(link.fields().sequence());
            }
            expectedPrevious = link.recordHash();
        }
        return OptionalLong.empty();
    }
}
