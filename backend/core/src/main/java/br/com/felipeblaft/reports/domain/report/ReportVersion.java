package br.com.felipeblaft.reports.domain.report;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fotografia imutável do conteúdo de um relatório numa versão (Princípio III, FR-027): nunca é
 * atualizada nem apagada.
 */
public record ReportVersion(
        UUID id,
        ReportType reportType,
        UUID reportId,
        int number,
        VersionContent content,
        VersionOrigin origin,
        UUID interactionId,
        Channel channel,
        String promptVersion,
        Instant createdAt,
        Instant contentRemovedAt) {

    public ReportVersion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(reportType);
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(content);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(createdAt);
        if (number < 1) {
            throw new IllegalArgumentException("versão começa em 1");
        }
    }

    public ReportVersion(
            UUID id,
            ReportType reportType,
            UUID reportId,
            int number,
            VersionContent content,
            VersionOrigin origin,
            UUID interactionId,
            Channel channel,
            String promptVersion,
            Instant createdAt) {
        this(
                id,
                reportType,
                reportId,
                number,
                content,
                origin,
                interactionId,
                channel,
                promptVersion,
                createdAt,
                null);
    }

    public ReportVersion withContentRemoved(Instant now) {
        return new ReportVersion(
                id,
                reportType,
                reportId,
                number,
                content,
                origin,
                interactionId,
                channel,
                promptVersion,
                createdAt,
                now);
    }

    public static ReportVersion create(
            ReportType type,
            UUID reportId,
            int number,
            VersionContent content,
            VersionOrigin origin,
            UUID interactionId,
            Channel channel,
            String promptVersion,
            Instant now) {
        return new ReportVersion(
                GeneratorId.create(),
                type,
                reportId,
                number,
                content,
                origin,
                interactionId,
                channel,
                promptVersion,
                now);
    }
}
