package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.audit.AuditTrailEntry;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Objetos de resposta da trilha de auditoria (schemas {@code AuditEvent*} do contrato da 007). */
final class AdminAuditDtos {

    private AdminAuditDtos() {}

    /** Um evento da trilha; os rótulos são resolvidos na exibição e nunca gravados. */
    record AuditEvent(
            UUID id,
            long sequence,
            Instant occurredAt,
            UUID actorId,
            String actorRole,
            String actorLabel,
            String origin,
            String operation,
            String targetType,
            UUID targetId,
            String targetLabel,
            Integer documentVersion,
            Integer itemCount,
            String result,
            String errorCode,
            boolean hasOriginHash) {

        /** Sem os rótulos, que são nomes. */
        @Override
        public String toString() {
            return "AuditEvent[sequence=" + sequence + "]";
        }
    }

    /** Página de eventos. */
    record AuditEventPage(List<AuditEvent> items, int page, int size, long total) {

        @Override
        public String toString() {
            return "AuditEventPage[page=" + page + ", size=" + size + ", total=" + total + "]";
        }
    }

    /** Situação da cadeia: nunca verificada, íntegra ou quebrada. */
    record AuditIntegrity(
            String status, Instant verifiedAt, long checkedEvents, Long firstBrokenSequence) {

        @Override
        public String toString() {
            return "AuditIntegrity[checkedEvents=" + checkedEvents + "]";
        }
    }

    static AuditEventPage page(PageResult<AuditTrailEntry> result, MessageCatalog catalog) {
        return new AuditEventPage(
                result.items().stream().map(entry -> event(entry, catalog)).toList(),
                result.page(),
                result.size(),
                result.total());
    }

    private static AuditEvent event(AuditTrailEntry entry, MessageCatalog catalog) {
        return new AuditEvent(
                entry.id(),
                entry.sequence(),
                entry.occurredAt(),
                entry.authorId(),
                entry.authorRole() == null ? "SYSTEM" : entry.authorRole().name(),
                entry.authorLabel(),
                entry.origin().name(),
                entry.operation(),
                entry.targetType(),
                entry.targetId(),
                targetLabel(entry, catalog),
                entry.documentVersion(),
                entry.itemCount(),
                entry.result().name(),
                entry.errorCode(),
                entry.hasOriginHash());
    }

    /** Aluno eliminado vira "Aluno removido · #XXXXXX" (seis caracteres do identificador). */
    private static String targetLabel(AuditTrailEntry entry, MessageCatalog catalog) {
        if (entry.targetErased() && entry.targetId() != null) {
            String shortId = entry.targetId().toString().replace("-", "").substring(0, 6);
            return catalog.text("AUDIT_ERASED_STUDENT", Map.of("id", shortId.toUpperCase()));
        }
        return entry.targetLabel();
    }

    static AuditIntegrity integrity(VerifyAuditChainUseCase.Result result, Instant verifiedAt) {
        return new AuditIntegrity(
                result.status().name(),
                verifiedAt,
                result.checkedEvents(),
                result.firstBrokenSequence());
    }
}
