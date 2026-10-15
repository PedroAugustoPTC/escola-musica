package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.PdfDocumentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PdfDocumentJpa extends JpaRepository<PdfDocumentEntity, UUID> {

    Optional<PdfDocumentEntity> findByVersionIdAndType(UUID versionId, String type);
}
