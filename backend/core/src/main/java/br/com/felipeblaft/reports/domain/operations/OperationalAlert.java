package br.com.felipeblaft.reports.domain.operations;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Alerta operacional (FR-055, FR-056). Só carrega tipo, componente e um código do motivo: nunca
 * aluno, professor nem texto livre. Há um único alerta ativo por tipo e componente.
 */
public record OperationalAlert(
        UUID id,
        AlertType type,
        String component,
        String detailCode,
        AlertStatus status,
        Instant startedAt,
        Instant lastSeenAt,
        Instant normalizedAt,
        int occurrences) {

    private static final Pattern COMPONENT = Pattern.compile("[a-z0-9._-]{1,64}");
    private static final Pattern DETAIL = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    public OperationalAlert {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        requireComponent(component);
        requireDetail(detailCode);
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(lastSeenAt);
        if (occurrences < 1) {
            throw new IllegalArgumentException("ocorrências devem ser maiores que zero");
        }
        if ((status == AlertStatus.NORMALIZED) != (normalizedAt != null)) {
            throw new IllegalArgumentException("normalização incoerente com a situação");
        }
    }

    /** Aceita só componente em minúsculas, dígitos e {@code . _ -} (sem espaço nem texto livre). */
    public static String requireComponent(String component) {
        if (component == null || !COMPONENT.matcher(component).matches()) {
            throw new IllegalArgumentException("componente de alerta inválido");
        }
        return component;
    }

    /** Aceita só um código em maiúsculas (sem espaço nem texto livre). */
    public static String requireDetail(String detailCode) {
        if (detailCode == null || !DETAIL.matcher(detailCode).matches()) {
            throw new IllegalArgumentException("código de motivo de alerta inválido");
        }
        return detailCode;
    }

    /**
     * Transforma o nome de um provedor em componente válido (minúsculas, sem caractere fora do
     * conjunto, no máximo 64); vazio vira {@code unknown}.
     */
    public static String componentOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String clean =
                raw.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    public boolean active() {
        return status == AlertStatus.ACTIVE;
    }
}
