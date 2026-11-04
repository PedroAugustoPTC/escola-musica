package br.com.felipeblaft.reports.domain.job;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de custo e tempo de uma chamada de IA (FR-056, R-13). O canal é o do pedido que originou
 * o trabalho (feature 007, FR-051). Não guarda prompt, resposta nem transcrição (FR-055). Provedor
 * e modelo vêm da configuração, opacos para o núcleo.
 */
public record AiCall(
        UUID id,
        UUID jobId,
        Channel channel,
        ReportType reportType,
        UUID reportId,
        AiTask task,
        String modelProvider,
        BigDecimal audioSeconds,
        Integer inputTokens,
        Integer outputTokens,
        long durationMs,
        BigDecimal estimatedCostUsd,
        boolean success,
        String errorCode,
        Instant createdAt) {

    public AiCall {
        Objects.requireNonNull(id);
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(task);
        Objects.requireNonNull(createdAt);
    }

    /** A mesma chamada vinculada a um relatório. */
    public AiCall withReport(ReportType type, UUID reportId) {
        return new AiCall(
                id,
                jobId,
                channel,
                type,
                reportId,
                task,
                modelProvider,
                audioSeconds,
                inputTokens,
                outputTokens,
                durationMs,
                estimatedCostUsd,
                success,
                errorCode,
                createdAt);
    }

    public static AiCall create(
            UUID jobId,
            Channel channel,
            ReportType reportType,
            UUID reportId,
            AiTask task,
            String modelProvider,
            BigDecimal audioSeconds,
            Integer inputTokens,
            Integer outputTokens,
            long durationMs,
            BigDecimal cost,
            boolean success,
            String errorCode,
            Instant now) {
        return new AiCall(
                GeneratorId.create(),
                jobId,
                channel,
                reportType,
                reportId,
                task,
                modelProvider,
                audioSeconds,
                inputTokens,
                outputTokens,
                durationMs,
                cost,
                success,
                errorCode,
                now);
    }
}
