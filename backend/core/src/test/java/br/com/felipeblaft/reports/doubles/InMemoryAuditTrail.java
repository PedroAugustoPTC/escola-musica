package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.audit.AuditAnchor;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.domain.audit.AuditTrailEntry;
import br.com.felipeblaft.reports.domain.audit.AuditTrailFilter;
import br.com.felipeblaft.reports.domain.audit.AuditVerification;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.port.AuditTrailRetentionPort;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A trilha de auditoria em memória: grava (as duas portas de gravação), consulta e expurga como o
 * banco, encadeando os eventos com {@link AuditChain}. {@link #tamper(long)} altera uma linha por
 * fora da cadeia, como faria o dono do banco, para provar a detecção.
 */
public class InMemoryAuditTrail
        implements AuthorshipRecorderPort, AuditTrailQueryPort, AuditTrailRetentionPort {

    private static class Row {
        final long sequence;
        final AuthorshipRecord record;
        final String previousHash;
        final String recordHash;
        AuthorshipRecord shown;

        Row(long sequence, AuthorshipRecord record, String previousHash, String recordHash) {
            this.sequence = sequence;
            this.record = record;
            this.previousHash = previousHash;
            this.recordHash = recordHash;
            this.shown = record;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<AuditAnchor> anchors = new ArrayList<>();
    private long nextSequence = 1;

    @Override
    public synchronized void record(AuthorshipRecord record) {
        String previous =
                rows.isEmpty()
                        ? anchors.isEmpty()
                                ? AuditChain.GENESIS_HASH
                                : anchors.get(anchors.size() - 1).lastRecordHash()
                        : rows.get(rows.size() - 1).recordHash;
        long sequence = nextSequence++;
        String hash = AuditChain.hash(previous, AuditChain.fieldsOf(record, sequence));
        rows.add(new Row(sequence, record, previous, hash));
    }

    @Override
    public void recordIndependent(AuthorshipRecord record) {
        record(record);
    }

    @Override
    public synchronized PageResult<AuditTrailEntry> query(AuditTrailFilter filter) {
        List<AuditTrailEntry> matching =
                rows.stream()
                        .filter(row -> matches(row.shown, filter))
                        .sorted(Comparator.comparingLong((Row row) -> row.sequence).reversed())
                        .map(InMemoryAuditTrail::entry)
                        .toList();
        return PageResult.slice(matching, filter.page());
    }

    private static boolean matches(AuthorshipRecord record, AuditTrailFilter filter) {
        LocalDate day = record.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        return !day.isBefore(filter.from())
                && !day.isAfter(filter.to())
                && (filter.actorId() == null || filter.actorId().equals(record.authorId()))
                && (filter.actorRole() == null || filter.actorRole() == record.authorRole())
                && (filter.origin() == null || filter.origin() == record.origin())
                && (filter.operation() == null || filter.operation().equals(record.operation()))
                && (filter.result() == null || filter.result() == record.result())
                && (filter.targetId() == null || filter.targetId().equals(record.targetId()));
    }

    private static AuditTrailEntry entry(Row row) {
        AuthorshipRecord r = row.shown;
        return new AuditTrailEntry(
                r.id(),
                row.sequence,
                r.occurredAt(),
                r.authorId(),
                r.authorRole(),
                null,
                r.origin(),
                r.operation(),
                r.targetType() == null ? null : r.targetType().name(),
                r.targetId(),
                null,
                false,
                r.readDetail() == null ? null : r.readDetail().documentVersion(),
                r.readDetail() == null ? null : r.readDetail().itemCount(),
                r.result(),
                r.errorCode(),
                r.originHash() != null);
    }

    @Override
    public synchronized List<AuditChain.Link> chainFrom(long afterSequence, int batchSize) {
        return rows.stream()
                .filter(row -> row.sequence > afterSequence)
                .sorted(Comparator.comparingLong(row -> row.sequence))
                .limit(batchSize)
                .map(
                        row ->
                                new AuditChain.Link(
                                        AuditChain.fieldsOf(row.shown, row.sequence),
                                        row.previousHash,
                                        row.recordHash))
                .toList();
    }

    @Override
    public synchronized Optional<AuditAnchor> latestAnchor() {
        return anchors.isEmpty() ? Optional.empty() : Optional.of(anchors.get(anchors.size() - 1));
    }

    @Override
    public synchronized Optional<AuditVerification> lastVerification() {
        return rows.stream()
                .filter(row -> row.record.operation().equals("AUDIT_TRAIL_VERIFY"))
                .max(Comparator.comparingLong(row -> row.sequence))
                .map(
                        row ->
                                new AuditVerification(
                                        row.record.occurredAt(),
                                        row.record.result()
                                                == br.com.felipeblaft.reports.domain.access
                                                        .AuthorshipResult.SUCCESS,
                                        row.record.readDetail() == null
                                                        || row.record.readDetail().itemCount()
                                                                == null
                                                ? 0
                                                : row.record.readDetail().itemCount()));
    }

    @Override
    public synchronized int purgeOlderThan(Instant cutoff) {
        long lastSequence = 0;
        for (Row row : rows) {
            if (!row.record.occurredAt().isBefore(cutoff)) {
                break;
            }
            lastSequence = row.sequence;
        }
        if (lastSequence == 0) {
            return 0;
        }
        final long until = lastSequence;
        String hash =
                rows.stream().filter(r -> r.sequence == until).findFirst().orElseThrow().recordHash;
        int before = rows.size();
        rows.removeIf(row -> row.sequence <= until);
        anchors.add(new AuditAnchor(until, hash));
        return before - rows.size();
    }

    /** Altera o resultado da linha por fora da cadeia (o dono do banco desligando o gatilho). */
    public synchronized void tamper(long sequence) {
        for (Row row : rows) {
            if (row.sequence == sequence) {
                row.shown =
                        new AuthorshipRecord(
                                row.record.id(),
                                row.record.occurredAt(),
                                row.record.authorId(),
                                row.record.authorRole(),
                                row.record.origin(),
                                row.record.operation(),
                                row.record.targetType(),
                                row.record.targetId(),
                                br.com.felipeblaft.reports.domain.access.AuthorshipResult.FAILURE,
                                "TAMPERED",
                                row.record.originHash(),
                                row.record.correlationId(),
                                row.record.readDetail());
            }
        }
    }

    public synchronized List<AuthorshipRecord> records() {
        return rows.stream().map(row -> row.record).toList();
    }
}
