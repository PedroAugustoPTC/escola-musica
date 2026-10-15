package br.com.felipeblaft.reports.domain.report;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resposta às perguntas ou pedido de alteração, por texto ou áudio (FR-056). O {@code texto} é o
 * digitado ou o transcrito: é o "texto entendido" mostrado ao professor (FR-024a, FR-027a).
 */
public record ReportInteraction(
        UUID id,
        ReportType reportType,
        UUID reportId,
        InteractionType type,
        Channel channel,
        Medium medium,
        String text,
        int baseVersion,
        Integer round,
        InteractionResult result,
        Integer resultingVersion,
        Instant createdAt) {

    public ReportInteraction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(reportType);
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(medium);
        Objects.requireNonNull(text);
        Objects.requireNonNull(result);
        Objects.requireNonNull(createdAt);
    }

    public static ReportInteraction create(
            ReportType reportType,
            UUID reportId,
            InteractionType type,
            Channel channel,
            Medium medium,
            String text,
            int baseVersion,
            Integer round,
            InteractionResult result,
            Integer resultingVersion,
            Instant now) {
        return new ReportInteraction(
                GeneratorId.create(),
                reportType,
                reportId,
                type,
                channel,
                medium,
                text,
                baseVersion,
                round,
                result,
                resultingVersion,
                now);
    }

    @Override
    public String toString() {
        return "ReportInteraction[id=" + id + ", type=" + type + ", result=" + result + "]";
    }
}
