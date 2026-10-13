package br.com.felipeblaft.reports.domain.report;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** PDF de uma versão (data-model.md §12). A chave de armazenamento nunca é exposta (R-11). */
public record PdfDocument(
        UUID id,
        UUID versionId,
        DocumentType type,
        String storageKey,
        long sizeBytes,
        String sha256,
        Instant generatedAt) {

    public PdfDocument {
        Objects.requireNonNull(id);
        Objects.requireNonNull(versionId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(storageKey);
        Objects.requireNonNull(sha256);
        Objects.requireNonNull(generatedAt);
    }

    public static PdfDocument create(
            UUID versionId, DocumentType type, String key, long size, String sha256, Instant now) {
        return new PdfDocument(GeneratorId.create(), versionId, type, key, size, sha256, now);
    }
}
