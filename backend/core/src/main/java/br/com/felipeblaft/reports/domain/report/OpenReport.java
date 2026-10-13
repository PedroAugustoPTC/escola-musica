package br.com.felipeblaft.reports.domain.report;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Visão derivada de um relatório em aberto (data-model.md, "Relatório em aberto"): relatório não
 * aprovado nem cancelado, ou registro ainda em transcrição (sem relatório criado).
 */
public record OpenReport(
        ReportType type,
        UUID reportId,
        UUID jobId,
        UUID studentId,
        String studentName,
        ReportStatus status,
        JobStep step,
        String periodKey,
        Channel originChannel,
        Instant updatedAt) {

    public OpenReport {
        Objects.requireNonNull(type);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(originChannel);
        Objects.requireNonNull(updatedAt);
    }

    /** Detalhes estruturados para o erro {@code RELATORIO_EM_ABERTO} (sem texto). */
    public Map<String, Object> details() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tipo", type);
        map.put("relatorioId", reportId);
        map.put("trabalhoId", jobId);
        map.put("alunoId", studentId);
        map.put("alunoNome", studentName);
        map.put("status", status);
        map.put("etapa", step);
        map.put("periodoChave", periodKey);
        map.put("canalOrigem", originChannel);
        map.put("atualizadoEm", updatedAt);
        return map;
    }
}
