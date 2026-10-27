package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Nome e e-mail validados de um professor (FR-018): nome com 2 a 120 caracteres, sem caracteres de
 * controle; e-mail com até 254 caracteres, no formato básico, guardado em minúsculas e sem espaços
 * nas pontas. Acumula as violações em um único {@code VALIDATION_FAILED} com {@code
 * details.fields}, sem repetir o valor recebido. O {@code toString} não revela o nome nem o e-mail.
 */
public record TeacherIdentity(String name, String email) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Campo e motivo de uma recusa. */
    public record Violation(String field, ErrorCode code) {}

    /** Valida e normaliza; recusa com {@code VALIDATION_FAILED} listando todas as violações. */
    public static TeacherIdentity of(String name, String email) {
        List<Violation> violations = violations(name, email);
        if (!violations.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, details(violations));
        }
        return new TeacherIdentity(normalizedName(name), Teacher.normalizeEmail(email));
    }

    /** Violações do nome e do e-mail, na ordem dos campos; vazio se tudo é válido. */
    public static List<Violation> violations(String name, String email) {
        List<Violation> violations = new ArrayList<>();
        String normalizedName = normalizedName(name);
        int size =
                normalizedName == null
                        ? 0
                        : normalizedName.codePointCount(0, normalizedName.length());
        if (size < RegistryLimits.NAME_MIN || size > RegistryLimits.NAME_MAX) {
            violations.add(new Violation("name", ErrorCode.INVALID_NAME));
        } else if (normalizedName.codePoints().anyMatch(Character::isISOControl)) {
            violations.add(new Violation("name", ErrorCode.INVALID_TEXT));
        }
        String normalizedEmail = Teacher.normalizeEmail(email);
        if (normalizedEmail == null
                || normalizedEmail.length() > RegistryLimits.EMAIL_MAX
                || !EMAIL.matcher(normalizedEmail).matches()) {
            violations.add(new Violation("email", ErrorCode.INVALID_EMAIL));
        }
        return violations;
    }

    /** Detalhes do erro no formato do contrato: {@code fields: [{field, code}]}. */
    public static Map<String, Object> details(List<Violation> violations) {
        List<Map<String, String>> fields =
                violations.stream()
                        .map(v -> Map.of("field", v.field(), "code", v.code().name()))
                        .toList();
        return Map.of("fields", fields);
    }

    private static String normalizedName(String name) {
        return name == null ? null : Normalizer.normalize(name.strip(), Normalizer.Form.NFC);
    }

    @Override
    public String toString() {
        return "TeacherIdentity";
    }
}
