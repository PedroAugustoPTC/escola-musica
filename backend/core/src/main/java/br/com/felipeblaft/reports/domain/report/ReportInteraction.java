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
        Instant createdAt,
        Instant textRemovedAt) {

    public ReportInteraction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(reportType);
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(medium);
        Objects.requireNonNull(result);
        Objects.requireNonNull(createdAt);
        if (text == null && textRemovedAt == null) {
            throw new NullPointerException("text");
        }
    }

    public ReportInteraction(
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
        this(
                id,
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
                createdAt,
                null);
    }

    /** Texto removido pela retenção: a linha fica, sem o texto. */
    public ReportInteraction withTextRemoved(Instant now) {
        return new ReportInteraction(
                id,
                reportType,
                reportId,
                type,
                channel,
                medium,
                null,
                baseVersion,
                round,
                result,
                resultingVersion,
                createdAt,
                now);
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
