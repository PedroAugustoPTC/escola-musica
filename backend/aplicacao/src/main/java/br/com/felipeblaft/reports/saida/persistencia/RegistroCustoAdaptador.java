package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.ChamadaIaEntidade;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custo e tempo por chamada de IA na tabela {@code chamada_ia} (FR-056); sem prompt nem resposta.
 */
@Repository
class RegistroCustoAdaptador implements CostRecordPort {

    private final ChamadaIaJpa jpa;

    RegistroCustoAdaptador(ChamadaIaJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void register(AiCall c) {
        ChamadaIaEntidade e = new ChamadaIaEntidade();
        e.id = c.id();
        e.trabalhoId = c.jobId();
        e.relatorioTipo = c.reportType() == null ? null : c.reportType().name();
        e.relatorioId = c.reportId();
        e.tarefa = c.task().name();
        e.provedorModelo = c.modelProvider();
        e.segundosAudio = c.audioSeconds();
        e.tokensEntrada = c.inputTokens();
        e.tokensSaida = c.outputTokens();
        e.duracaoMs = c.durationMs();
        e.custoEstimadoUsd = c.estimatedCostUsd();
        e.sucesso = c.success();
        e.codigoErro = c.errorCode();
        e.criadoEm = c.createdAt();
        jpa.save(e);
    }

    @Override
    @Transactional
    public void linkReport(UUID trabalhoId, ReportType tipo, UUID relatorioId) {
        jpa.vincularRelatorio(trabalhoId, tipo.name(), relatorioId);
    }
}
