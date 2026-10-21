package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Princípio IV: o {@code toString} de comandos, resultados e registros da feature 005 nunca contém
 * dado pessoal, senha, CPF nem código. Cada record dos pacotes abaixo é construído com um valor
 * sentinela em todo texto e em todo {@link Secret}; o sentinela não pode aparecer na representação
 * textual (que é o que chegaria a um log ou a uma mensagem de erro).
 */
class PersonalDataToStringTest {

    private static final String SENTINEL = "PERSONAL-DATA-SENTINEL-4711";
    private static final String BASE = "br.com.felipeblaft.reports";

    /** Campos de texto que carregam só identificadores técnicos, nunca dado pessoal. */
    private static final Map<String, String> TECHNICAL_FIELDS =
            Map.of(
                    "correlationId", "req-1",
                    "errorCode", "TECHNICAL",
                    "operation", "TECHNICAL",
                    "originHash", "0123456789abcdef".repeat(4),
                    // nome de seção e de campo da carga inicial, nunca o valor
                    "section", "students",
                    "field", "birthDate");

    private static final List<String> PACKAGES =
            List.of(
                    BASE + ".usecase.administrator",
                    BASE + ".usecase.teacher",
                    BASE + ".usecase.registry",
                    BASE + ".usecase.seed",
                    BASE + ".domain.access",
                    BASE + ".domain.registry",
                    BASE + ".domain.telegram.StudentRegistrationDraft",
                    BASE + ".service.ActivationCodeIssuanceService");

    @Test
    void noRecordOfTheFeatureRevealsPersonalDataInToString() throws Exception {
        List<String> leaks = new ArrayList<>();
        int checked = 0;
        for (JavaClass type :
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(BASE)) {
            if (!type.isRecord()
                    || PACKAGES.stream().noneMatch(p -> type.getName().startsWith(p))) {
                continue;
            }
            checked++;
            String text = sample(type.reflect()).toString();
            if (text.contains(SENTINEL)) {
                leaks.add(type.getName());
            }
        }
        assertThat(checked).as("records verificados").isGreaterThan(30);
        assertThat(leaks).as("records cujo toString revela dado pessoal").isEmpty();
    }

    /** Constrói o record com sentinelas em todo texto, recursivamente. */
    private static Object sample(Class<?> type) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            arguments[i] = value(types[i], components[i].getName());
        }
        Constructor<?> constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Object value(Class<?> type, String name) throws Exception {
        if (type == String.class) {
            return TECHNICAL_FIELDS.getOrDefault(name, SENTINEL);
        }
        if (type == Secret.class) {
            return new Secret(SENTINEL);
        }
        if (type == Cpf.class) {
            return Cpf.of("529.982.247-25");
        }
        if (type == UUID.class) {
            return UUID.randomUUID();
        }
        if (type == Instant.class) {
            return Instant.parse("2026-10-20T12:00:00Z");
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2014, 5, 10);
        }
        if (type == Duration.class) {
            return Duration.ofMinutes(1);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.TRUE;
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        if (type == long.class) {
            return 1L;
        }
        if (type == byte[].class) {
            return SENTINEL.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (type == List.class) {
            return List.of();
        }
        if (type == Set.class) {
            return Set.of();
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type.isRecord() && type.getName().startsWith(BASE)) {
            return sample(type);
        }
        return null;
    }
}
