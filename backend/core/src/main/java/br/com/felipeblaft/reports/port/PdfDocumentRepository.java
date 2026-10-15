package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import java.util.Optional;
import java.util.UUID;

public interface PdfDocumentRepository {

    void save(PdfDocument document);

    Optional<PdfDocument> search(UUID versionId, DocumentType type);
}
