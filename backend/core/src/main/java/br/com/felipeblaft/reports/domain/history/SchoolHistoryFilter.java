package br.com.felipeblaft.reports.domain.history;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Filtro validado da visão da escola (feature 006, US3): aluno, professor, tipo e período
 * (inclusivo) mais a página. Produz o {@link ApprovedReportCriteria} e o {@link PageRequest}. Toda
 * recusa é {@code VALIDATION_FAILED} com {@code details.fields[]} (campo e código), sem repetir o
 * valor recebido.
 */
public record SchoolHistoryFilter(
        UUID studentId,
        UUID teacherId,
        ReportType type,
        LocalDate from,
        LocalDate to,
        PageRequest page) {

    public static final String INVALID_DATE = "INVALID_DATE";
    public static final String INVALID_REPORT_TYPE = "INVALID_REPORT_TYPE";
    public static final String PERIOD_START_AFTER_END = "PERIOD_START_AFTER_END";
    public static final String INVALID_PAGE = ErrorCode.INVALID_DATA.name();

    public SchoolHistoryFilter {
        Objects.requireNonNull(page);
        if (from != null && to != null && from.isAfter(to)) {
            throw validation(List.of(violation("from", PERIOD_START_AFTER_END)));
        }
    }

    /** Filtro com a forma conferida (datas, tipo, página), ainda sem a ordem do período. */
    public record Syntax(
            UUID studentId,
            UUID teacherId,
            ReportType type,
            LocalDate from,
            LocalDate to,
            PageRequest page) {}

    /**
     * Confere só a forma dos valores (datas, tipo, página e tamanho), acumulando as violações. A
     * ordem do período fica para o caso de uso, que grava a recusa na autoria.
     */
    public static Syntax parseSyntax(
            UUID studentId,
            UUID teacherId,
            String typeText,
            String fromText,
            String toText,
            Integer pageNumber,
            Integer pageSize) {
        List<Map<String, String>> violations = new ArrayList<>();
        LocalDate from = date("from", fromText, violations);
        LocalDate to = date("to", toText, violations);
        ReportType type = type(typeText, violations);
        PageRequest page = pageRequest(pageNumber, pageSize, violations);
        if (!violations.isEmpty()) {
            throw validation(violations);
        }
        return new Syntax(studentId, teacherId, type, from, to, page);
    }

    public ApprovedReportCriteria criteria() {
        return new ApprovedReportCriteria(studentId, teacherId, type, from, to);
    }

    private static LocalDate date(String field, String text, List<Map<String, String>> out) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            out.add(violation(field, INVALID_DATE));
            return null;
        }
    }

    private static ReportType type(String text, List<Map<String, String>> out) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (ReportType candidate : ReportType.values()) {
            if (candidate.name().equals(text.strip())) {
                return candidate;
            }
        }
        out.add(violation("type", INVALID_REPORT_TYPE));
        return null;
    }

    private static PageRequest pageRequest(
            Integer number, Integer size, List<Map<String, String>> out) {
        int pageValue = number == null ? 0 : number;
        int sizeValue = size == null ? PageRequest.DEFAULT_SIZE : size;
        boolean valid = true;
        if (pageValue < 0) {
            out.add(violation("page", INVALID_PAGE));
            valid = false;
        }
        if (sizeValue < 1 || sizeValue > PageRequest.MAX_SIZE) {
            out.add(violation("size", INVALID_PAGE));
            valid = false;
        }
        return valid ? new PageRequest(pageValue, sizeValue) : PageRequest.first();
    }

    private static Map<String, String> violation(String field, String code) {
        return Map.of("field", field, "code", code);
    }

    private static DomainException validation(List<Map<String, String>> violations) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, Map.of("fields", violations));
    }
}
