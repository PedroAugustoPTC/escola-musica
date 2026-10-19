package br.com.felipeblaft.reports.architecture;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras da evidência de paridade (feature 004, FR-029, data-model §5): lê o conteúdo de {@code
 * specs/004-n8n-telegram-transport/evidence/parity.md} e devolve as violações. O arquivo traz uma
 * tabela de campos; {@code mode} decide o que é exigido: {@code evidence} (a prova completa) ou
 * {@code waiver} (a dispensa formal do mantenedor, sem dado inventado).
 */
final class ParityEvidenceGuard {

    static final String PATH = "specs/004-n8n-telegram-transport/evidence/parity.md";

    private static final int MIN_OBSERVATION_DAYS = 7;
    private static final Pattern ROW =
            Pattern.compile("^\\|\\s*`([A-Za-z]+)`\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Pattern DATE_AT_START = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern ISOLATED_DUPLICATE =
            Pattern.compile(
                    "^1\\b.*\\d{4}-\\d{2}-\\d{2}.*actionId\\s*=\\s*\\S+.*accept.*$",
                    Pattern.CASE_INSENSITIVE);
    private static final List<String> EMPTY_VALUES =
            List.of("", "-", "n/a", "tbd", "todo", "not recorded", "nao registrado", "pending");

    private ParityEvidenceGuard() {}

    /** Violações do conteúdo; {@code null} significa arquivo ausente. */
    static List<String> check(String content) {
        List<String> violations = new ArrayList<>();
        if (content == null) {
            violations.add("o arquivo " + PATH + " não existe (FR-029)");
            return violations;
        }
        Map<String, String> fields = fields(content);
        String mode = fields.getOrDefault("mode", "");
        switch (mode) {
            case "waiver" -> checkWaiver(fields, violations);
            case "evidence" -> checkEvidence(fields, violations);
            default -> violations.add("mode deve ser evidence ou waiver, mas é \"" + mode + "\"");
        }
        return violations;
    }

    static Map<String, String> fields(String content) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            Matcher row = ROW.matcher(line);
            if (row.matches()) {
                fields.put(row.group(1), unquote(row.group(2)));
            }
        }
        return fields;
    }

    private static void checkWaiver(Map<String, String> fields, List<String> violations) {
        requireText(fields, "waivedBy", violations);
        requireDate(fields, "waivedAt", violations);
        requireText(fields, "reason", violations);
    }

    private static void checkEvidence(Map<String, String> fields, List<String> violations) {
        requireDate(fields, "switchedAt", violations);
        requireText(fields, "suiteRun", violations);
        requireText(fields, "approvedBy", violations);
        requireDate(fields, "approvedAt", violations);
        Integer days = integer(fields, "observationDays", violations);
        if (days != null && days < MIN_OBSERVATION_DAYS) {
            violations.add(
                    "observationDays deve ser pelo menos "
                            + MIN_OBSERVATION_DAYS
                            + ", mas é "
                            + days);
        }
        requireZero(fields, "updatesWithoutReply", violations);
        requireZero(fields, "proactiveDefinitiveFailuresFromTransport", violations);
        requireZero(fields, "lostCallbackConfirmations", violations);
        checkDuplicateReplies(fields, violations);
        String reports = normalize(fields.getOrDefault("teacherReports", ""));
        if (!reports.equals("none")) {
            violations.add("teacherReports deve ser none, mas é \"" + reports + "\"");
        }
    }

    /** Zero, ou uma repetição isolada com data, {@code actionId} e aceite por escrito. */
    private static void checkDuplicateReplies(Map<String, String> fields, List<String> violations) {
        String value = fields.getOrDefault("duplicateReplies", "");
        if (value.equals("0") || ISOLATED_DUPLICATE.matcher(value).matches()) {
            return;
        }
        violations.add(
                "duplicateReplies deve ser 0 (ou 1 com data, actionId=<id> e a aceitação por"
                        + " escrito, com a palavra accepted), mas é \""
                        + value
                        + "\"");
    }

    private static void requireZero(Map<String, String> fields, String name, List<String> out) {
        Integer value = integer(fields, name, out);
        if (value != null && value != 0) {
            out.add(name + " deve ser 0, mas é " + value);
        }
    }

    private static Integer integer(Map<String, String> fields, String name, List<String> out) {
        String value = fields.getOrDefault(name, "");
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException invalid) {
            out.add(name + " deve ser um número inteiro, mas é \"" + value + "\"");
            return null;
        }
    }

    private static void requireText(Map<String, String> fields, String name, List<String> out) {
        if (EMPTY_VALUES.contains(normalize(fields.getOrDefault(name, "")))) {
            out.add(name + " é obrigatório e está vazio");
        }
    }

    private static void requireDate(Map<String, String> fields, String name, List<String> out) {
        String value = fields.getOrDefault(name, "");
        Matcher start = DATE_AT_START.matcher(value);
        try {
            if (!start.find()) {
                throw new DateTimeParseException("sem data", value, 0);
            }
            LocalDate.parse(start.group(1));
        } catch (DateTimeParseException invalid) {
            out.add(name + " deve começar com uma data AAAA-MM-DD, mas é \"" + value + "\"");
        }
    }

    private static String unquote(String value) {
        String text = value.trim();
        if (text.length() >= 2 && text.startsWith("`") && text.endsWith("`")) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
}
