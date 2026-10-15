package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import java.util.UUID;

/** Registro de custo e tempo das chamadas de IA (FR-056). */
public interface CostRecordPort {

    void register(AiCall call);

    /**
     * Vincula ao relatório recém-criado as chamadas do trabalho feitas antes de ele existir (a
     * transcrição inicial e a consolidação); as já vinculadas não mudam.
     */
    void linkReport(UUID jobId, ReportType type, UUID reportId);
}
