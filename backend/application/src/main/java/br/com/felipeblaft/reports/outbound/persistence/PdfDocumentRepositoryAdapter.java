package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PdfDocumentRepositoryAdapter implements PdfDocumentRepository {

    private final PdfDocumentJpa jpa;

    PdfDocumentRepositoryAdapter(PdfDocumentJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(PdfDocument document) {
        jpa.saveAndFlush(LessonReportMapper.toEntity(document));
    }

    @Override
    public Optional<PdfDocument> search(UUID versionId, DocumentType type) {
        return jpa.findByVersionIdAndType(versionId, type.name()).map(LessonReportMapper::toDomain);
    }
}
