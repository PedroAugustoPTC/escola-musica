package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram;
import br.com.felipeblaft.reports.inbound.web.MyStudentDto;
import br.com.felipeblaft.reports.inbound.web.MyStudentPageDto;
import br.com.felipeblaft.reports.inbound.web.admin.AdminPageResponse;
import br.com.felipeblaft.reports.inbound.web.admin.AdminTeacherDtos.AdminTeacherResponse;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
 * Princípio IV nos adaptadores de entrada: o {@code toString} dos registros de requisição e
 * resposta da área administrativa, de "Meus alunos" e do update do Telegram nunca contém nome,
 * e-mail, telefone, texto digitado, código nem senha (é o que chegaria a um log ou a uma mensagem
 * de erro). Cada record é construído com um valor sentinela em todo texto; o sentinela não pode
 * aparecer. Espelha {@code PersonalDataToStringTest} do núcleo.
 */
class InboundPersonalDataToStringTest {

    private static final String SENTINEL = "PERSONAL-DATA-SENTINEL-4711";
    private static final String BASE = "br.com.felipeblaft.reports";

    /**
     * Campos de texto que carregam só identificadores técnicos (o {@code id} do toque é do
     * Telegram).
     */
    private static final Map<String, String> TECHNICAL_FIELDS =
            Map.of("type", "private", "activationState", "ACTIVE", "id", "cb-1");

    private static final List<String> SCOPES =
            List.of(
                    BASE + ".inbound.web.admin.",
                    BASE + ".inbound.web.MyStudent",
                    BASE + ".inbound.telegram.UpdateTelegram");

    @Test
    void noRecordOfTheAdminMyStudentsAndTelegramInputRevealsPersonalDataInToString()
            throws Exception {
        List<String> leaks = new ArrayList<>();
        int checked = 0;
        for (JavaClass type :
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(BASE + ".inbound")) {
            if (!type.isRecord() || SCOPES.stream().noneMatch(p -> type.getName().startsWith(p))) {
                continue;
            }
            checked++;
            if (sample(type.reflect()).toString().contains(SENTINEL)) {
                leaks.add(type.getName());
            }
        }
        assertThat(checked).as("records verificados").isGreaterThan(20);
        assertThat(leaks).as("records cujo toString revela dado pessoal").isEmpty();
    }

    @Test
    void pagesDoNotPrintTheirItems() {
        AdminTeacherResponse teacher =
                new AdminTeacherResponse(UUID.randomUUID(), SENTINEL, SENTINEL, true, false);

        assertThat(new AdminPageResponse<>(List.of(teacher), 0, 20, 1).toString())
                .doesNotContain(SENTINEL);
        assertThat(teacher.toString()).doesNotContain(SENTINEL);
        MyStudentDto student =
                new MyStudentDto(
                        UUID.randomUUID(),
                        SENTINEL,
                        LocalDate.of(2014, 5, 10),
                        11,
                        SENTINEL,
                        true,
                        null,
                        Boolean.TRUE);
        assertThat(new MyStudentPageDto(List.of(student), 0, 20, 1).toString())
                .doesNotContain(SENTINEL);
    }

    @Test
    void telegramMessageAndCallbackDoNotPrintTextOrCallbackData() {
        UpdateTelegram.TelegramMessage message =
                new UpdateTelegram.TelegramMessage(
                        7L,
                        new UpdateTelegram.TelegramUser(1L, false),
                        new UpdateTelegram.ChatTelegram(1L, "private"),
                        SENTINEL,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        UpdateTelegram.CallbackTelegram callback =
                new UpdateTelegram.CallbackTelegram(
                        "cb1", new UpdateTelegram.TelegramUser(1L, false), message, SENTINEL);

        assertThat(message.toString()).doesNotContain(SENTINEL);
        assertThat(callback.toString()).doesNotContain(SENTINEL);
        assertThat(new UpdateTelegram(9L, message, callback).toString()).doesNotContain(SENTINEL);
    }

    // ---- sentinelas

    private static Object sample(Class<?> type) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            arguments[i] = value(types[i], components[i].getGenericType(), components[i].getName());
        }
        Constructor<?> constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Object value(Class<?> type, Type generic, String name) throws Exception {
        if (type == String.class) {
            return TECHNICAL_FIELDS.getOrDefault(name, SENTINEL);
        }
        if (type == Secret.class) {
            return new Secret(SENTINEL);
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
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == byte[].class) {
            return SENTINEL.getBytes(StandardCharsets.UTF_8);
        }
        if (type == List.class) {
            return listOf(generic);
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

    /** Lista com um elemento quando o tipo do elemento é um record conhecido. */
    private static List<Object> listOf(Type generic) throws Exception {
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element
                && element.isRecord()
                && element.getName().startsWith(BASE)) {
            return List.of(sample(element));
        }
        return List.of();
    }
}
