package br.com.felipeblaft.reports.domain.audit;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filtro validado da consulta da trilha (FR-045): o período é obrigatório e vai de uma data a
 * outra, inclusive, com no máximo 90 dias (89 dias entre as datas). Os demais filtros são
 * opcionais. Toda recusa é {@code VALIDATION_FAILED} com {@code details.fields[]}, sem repetir o
 * valor recebido.
 *
 * @param actorRole papel do autor; {@code null} no filtro "qualquer"
 * @param targetId aluno, relatório, professor etc.; filtrar por aluno inclui os eventos dos
 *     relatórios e consentimentos dele (a junção é da consulta)
 */
public record AuditTrailFilter(
        LocalDate from,
        LocalDate to,
        UUID actorId,
        Role actorRole,
        ActionOrigin origin,
        String operation,
        AuthorshipResult result,
        UUID targetId,
        PageRequest page) {

    /** Maior distância entre as duas datas do período (período de 90 dias, inclusive). */
    public static final int MAX_DAYS_BETWEEN = 89;

    public static final String PERIOD_REQUIRED = "PERIOD_REQUIRED";
    public static final String PERIOD_START_AFTER_END = "PERIOD_START_AFTER_END";
    public static final String PERIOD_TOO_LONG = "PERIOD_TOO_LONG";
    public static final String INVALID_OPERATION = "INVALID_OPERATION";

    private static final Pattern OPERATION = Pattern.compile("[A-Z][A-Z0-9_]{0,47}");

    public AuditTrailFilter {
        Objects.requireNonNull(page);
        if (from == null || to == null) {
            throw refusal("period", PERIOD_REQUIRED);
        }
        if (from.isAfter(to)) {
            throw refusal("period", PERIOD_START_AFTER_END);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS_BETWEEN) {
            throw refusal("period", PERIOD_TOO_LONG);
        }
        if (operation != null && !OPERATION.matcher(operation).matches()) {
            throw refusal("operation", INVALID_OPERATION);
        }
    }

    private static DomainException refusal(String field, String code) {
        return new DomainException(
                ErrorCode.VALIDATION_FAILED,
                Map.of("fields", List.of(Map.of("field", field, "code", code))));
    }
}
