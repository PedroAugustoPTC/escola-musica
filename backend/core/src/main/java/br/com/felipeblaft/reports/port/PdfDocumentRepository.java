package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PdfDocumentRepository {

    void save(PdfDocument document);

    Optional<PdfDocument> search(UUID versionId, DocumentType type);

    /** Ids de PDFs de versão não aprovada de relatório aprovado vencido, em ordem de id. */
    List<UUID> findRetentionCandidates(
            RetentionPolicy policy, Instant now, int limit, UUID afterId);

    /**
     * Lê o PDF bloqueando a linha do relatório até o fim da transação (corrida com a aprovação).
     */
    Optional<RetentionPdfCandidate> findForRetention(UUID pdfId);

    /** Marca o PDF como removido ({@code content_removed_at}); o arquivo sai depois do commit. */
    void markContentRemoved(UUID pdfId, Instant now);

    /** Marca todos os PDFs do relatório (anonimização); devolve quantos foram marcados. */
    int markContentRemovedForReport(ReportType type, UUID reportId, Instant now);

    /** Quantos PDFs o relatório tem (todas as versões), para o alcance da eliminação. */
    int countByReport(ReportType type, UUID reportId);

    /** PDFs marcados cujo arquivo ainda não foi removido do armazenamento. */
    List<PendingFileRemoval> findPendingFileRemovals(int limit);

    void markFileRemoved(UUID pdfId, Instant now);
}
