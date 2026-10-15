package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.AiCallEntity;
import br.com.felipeblaft.reports.port.CostRecordPort;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custo e tempo por chamada de IA na tabela {@code chamada_ia} (FR-056); sem prompt nem resposta.
 */
@Repository
class CostRecordAdapter implements CostRecordPort {

    private final AiCallJpa jpa;

    CostRecordAdapter(AiCallJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void register(AiCall c) {
        AiCallEntity e = new AiCallEntity();
        e.id = c.id();
        e.jobId = c.jobId();
        e.reportType = c.reportType() == null ? null : c.reportType().name();
        e.reportId = c.reportId();
        e.task = c.task().name();
        e.providerModel = c.modelProvider();
        e.audioSeconds = c.audioSeconds();
        e.inputTokens = c.inputTokens();
        e.outputTokens = c.outputTokens();
        e.durationMs = c.durationMs();
        e.estimatedCostUsd = c.estimatedCostUsd();
        e.success = c.success();
        e.errorCode = c.errorCode();
        e.createdAt = c.createdAt();
        jpa.save(e);
    }

    @Override
    @Transactional
    public void linkReport(UUID jobId, ReportType type, UUID reportId) {
        jpa.linkReport(jobId, type.name(), reportId);
    }
}
