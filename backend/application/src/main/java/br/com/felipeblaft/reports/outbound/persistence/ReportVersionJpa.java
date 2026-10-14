package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.ReportVersionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReportVersionJpa extends JpaRepository<ReportVersionEntity, UUID> {

    Optional<ReportVersionEntity> findByReportTypeAndReportIdAndNumber(
            String reportType, UUID reportId, int number);
}
