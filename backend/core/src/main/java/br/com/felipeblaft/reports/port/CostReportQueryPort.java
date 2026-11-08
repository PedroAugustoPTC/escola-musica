package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiTask;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Leitura das chamadas de IA de um período, para o relatório de custo (FR-053). */
public interface CostReportQueryPort {

    /**
     * Uma chamada de IA. {@code reportId} e {@code studentId} são nulos quando a chamada não chegou
     * a um relatório.
     */
    record CostLine(
            ReportType reportType,
            UUID reportId,
            UUID studentId,
            AiTask task,
            Channel channel,
            String provider,
            BigDecimal costUsd,
            long durationMs,
            boolean success) {}

    /** Chamadas feitas de {@code from} (inclusive) até {@code to} (exclusive). */
    List<CostLine> lines(Instant from, Instant to);

    /**
     * Nomes dos alunos, só para exibir na tela de custo do administrador. Alunos já eliminados não
     * aparecem no resultado.
     */
    default Map<UUID, String> studentNames(Collection<UUID> studentIds) {
        return Map.of();
    }
}
