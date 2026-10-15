package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.ReportInteractionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReportInteractionJpa extends JpaRepository<ReportInteractionEntity, UUID> {

    Optional<ReportInteractionEntity> findFirstByReportTypeAndReportIdOrderByCreatedAtDescIdDesc(
            String reportType, UUID reportId);
}
