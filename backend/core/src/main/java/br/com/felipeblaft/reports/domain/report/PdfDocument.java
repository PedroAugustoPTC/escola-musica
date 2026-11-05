package br.com.felipeblaft.reports.domain.report;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.privacy.RetentionDecision;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PDF de uma versão (data-model.md §12). A chave de armazenamento nunca é exposta (R-11).
 * {@code contentRemovedAt} marca o arquivo removido pela retenção; a linha permanece.
 */
public record PdfDocument(
        UUID id,
        UUID versionId,
        DocumentType type,
        String storageKey,
        long sizeBytes,
        String sha256,
        Instant generatedAt,
        Instant contentRemovedAt) {

    public PdfDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(versionId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(storageKey);
        Objects.requireNonNull(sha256);
        Objects.requireNonNull(generatedAt);
    }

    public PdfDocument(
            UUID id,
            UUID versionId,
            DocumentType type,
            String storageKey,
            long sizeBytes,
            String sha256,
            Instant generatedAt) {
        this(id, versionId, type, storageKey, sizeBytes, sha256, generatedAt, null);
    }

    public static PdfDocument create(
            UUID versionId, DocumentType type, String key, long size, String sha256, Instant now) {
        return new PdfDocument(GeneratorId.create(), versionId, type, key, size, sha256, now);
    }

    /**
     * PDF de versão que não é a aprovada vigente, de relatório aprovado há mais que o prazo: removível
     * (FR-012). O PDF da versão aprovada nunca é (FR-011).
     *
     * @param approvedVersionPdf este é o PDF da versão aprovada vigente
     * @param reportApprovedAt aprovação do relatório; {@code null} se não aprovado (em aberto: fica)
     */
    public RetentionDecision retentionAction(
            RetentionPolicy policy,
            Instant now,
            boolean approvedVersionPdf,
            Instant reportApprovedAt) {
        if (approvedVersionPdf || contentRemovedAt != null) {
            return RetentionDecision.KEEP;
        }
        return RetentionDecision.expired(reportApprovedAt, policy.unapprovedPdfDays().value(), now)
                ? RetentionDecision.REMOVE_UNAPPROVED_PDFS
                : RetentionDecision.KEEP;
    }

    public PdfDocument withContentRemoved(Instant now) {
        return new PdfDocument(id, versionId, type, storageKey, sizeBytes, sha256, generatedAt, now);
    }
}
