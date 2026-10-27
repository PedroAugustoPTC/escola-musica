package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Entrada validada de aluno (R-11): valida todos os campos e acumula as violações em um único
 * {@code VALIDATION_FAILED} com {@code details.fields} (lista de {@code field} e {@code code}); a
 * falha nunca repete o valor recebido.
 *
 * @param cpf obrigatório no cadastro; {@code null} na edição que mantém o CPF atual
 * @param instrument opcional
 * @param guardian obrigatório e completo se o aluno é menor de 18 anos na data de referência; senão
 *     opcional (e, em aluno antigo da 001, pode ter só o nome)
 */
public record StudentData(
        PersonName name, BirthDate birthDate, Cpf cpf, Instrument instrument, Guardian guardian) {

    /**
     * Valida os textos brutos de um cadastro (o CPF é obrigatório). {@code today} é a data de hoje
     * no fuso da escola.
     */
    public static StudentData of(
            String name,
            String birthDate,
            String cpf,
            String instrument,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            LocalDate today) {
        return build(
                true,
                name,
                birthDate,
                cpf,
                instrument,
                guardianName,
                guardianPhone,
                guardianEmail,
                today);
    }

    /** Como {@link #of}, para a edição: CPF em branco mantém o atual e fica {@code null}. */
    public static StudentData forEdit(
            String name,
            String birthDate,
            String cpf,
            String instrument,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            LocalDate today) {
        return build(
                false,
                name,
                birthDate,
                cpf,
                instrument,
                guardianName,
                guardianPhone,
                guardianEmail,
                today);
    }

    private static StudentData build(
            boolean cpfRequired,
            String name,
            String birthDate,
            String cpf,
            String instrument,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            LocalDate today) {
        List<Map<String, String>> violations = new ArrayList<>();
        PersonName personName = attempt(violations, "name", () -> PersonName.of(name));
        BirthDate birth = attempt(violations, "birthDate", () -> BirthDate.parse(birthDate, today));
        Cpf validCpf = null;
        if (!isBlank(cpf)) {
            validCpf = attempt(violations, "cpf", () -> Cpf.of(cpf));
        } else if (cpfRequired) {
            violations.add(violation("cpf", ErrorCode.INVALID_CPF));
        }
        Instrument validInstrument =
                isBlank(instrument)
                        ? null
                        : attempt(violations, "instrument", () -> Instrument.of(instrument));
        Guardian guardian =
                guardian(
                        violations,
                        birth != null && !birth.isAdultAt(today),
                        guardianName,
                        guardianPhone,
                        guardianEmail);
        if (!violations.isEmpty()) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED, Map.of("fields", List.copyOf(violations)));
        }
        return new StudentData(personName, birth, validCpf, validInstrument, guardian);
    }

    private static Guardian guardian(
            List<Map<String, String>> violations,
            boolean minor,
            String name,
            String phone,
            String email) {
        boolean hasName = !isBlank(name);
        boolean hasPhone = !isBlank(phone);
        boolean hasEmail = !isBlank(email);
        boolean allAbsent = !hasName && !hasPhone && !hasEmail;
        boolean anyAbsent = !hasName || !hasPhone || !hasEmail;
        PersonName guardianName =
                hasName ? attempt(violations, "guardian.name", () -> PersonName.of(name)) : null;
        BrazilianPhone guardianPhone =
                hasPhone
                        ? attempt(violations, "guardian.phone", () -> BrazilianPhone.of(phone))
                        : null;
        EmailAddress guardianEmail =
                hasEmail
                        ? attempt(violations, "guardian.email", () -> EmailAddress.of(email))
                        : null;
        if (minor && anyAbsent) {
            violations.add(violation("guardian", ErrorCode.GUARDIAN_REQUIRED));
        } else if (!allAbsent && !hasName) {
            violations.add(violation("guardian.name", ErrorCode.INVALID_NAME));
        }
        return guardianName == null
                ? null
                : new Guardian(guardianName, guardianPhone, guardianEmail);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static <T> T attempt(
            List<Map<String, String>> violations, String field, Supplier<T> parse) {
        try {
            return parse.get();
        } catch (DomainException error) {
            violations.add(violation(field, error.code()));
            return null;
        }
    }

    private static Map<String, String> violation(String field, ErrorCode code) {
        return Map.of("field", field, "code", code.name());
    }

    @Override
    public String toString() {
        return "StudentData";
    }
}
