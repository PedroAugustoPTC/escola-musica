package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.AiCallEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AiCallJpa extends JpaRepository<AiCallEntity, UUID> {

    /** Vincula ao relatório as chamadas do trabalho ainda sem relatório. */
    @Modifying
    @Query(
            "update AiCallEntity c set c.reportType = :type, c.reportId = :reportId"
                    + " where c.jobId = :jobId and c.reportId is null")
    int linkReport(UUID jobId, String type, UUID reportId);
}
