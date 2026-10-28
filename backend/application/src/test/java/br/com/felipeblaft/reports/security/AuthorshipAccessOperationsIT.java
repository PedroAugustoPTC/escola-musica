package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Registro de autoria do acesso do professor (FR-030, FR-031, FR-040): a reemissão do código de
 * acesso e a desvinculação do Telegram deixam registro do administrador, no canal Web, com o
 * professor como alvo; o código (o antigo e o novo) nunca é gravado.
 */
class AuthorshipAccessOperationsIT extends AdministrationStackBase {

    private AdministratorFixture administrator;
    private Cookie session;
    private TeacherAccount teacher;

    @BeforeEach
    void prepare() throws Exception {
        administrator = insertAdministrator();
        session = signInAdministrator(administrator);
        teacher = createTeacher(session, "Clara Souza", "clara@example.test");
        sendText(USER_CLARA, teacher.accessCode());
        clearAuthorship();
    }

    private void assertAdministratorRecord(Map<String, Object> record) {
        assertRecord(record, "ADMINISTRATOR", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(administrator.administrator().id());
        assertThat(record.get("target_type")).isEqualTo("TEACHER");
        assertThat(record.get("target_id")).isEqualTo(teacher.id());
        assertThat(record.get("origin_hash")).isNull();
        assertThat(UUID.fromString((String) record.get("correlation_id"))).isNotNull();
    }

    @Test
    void reissueLeavesARecordForEachOptionAndNeverTheCode() throws Exception {
        MockHttpServletResponse keeping =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/access-code",
                        session,
                        Map.of("endTelegramLink", false));
        MockHttpServletResponse ending =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/access-code",
                        session,
                        Map.of("endTelegramLink", true));

        assertThat(keeping.getStatus()).isEqualTo(200);
        assertThat(ending.getStatus()).isEqualTo(200);
        List<Map<String, Object>> records = authorship("TEACHER_REISSUE_ACCESS_CODE");
        assertThat(records).hasSize(2);
        records.forEach(this::assertAdministratorRecord);
        assertThat(authorshipDump())
                .doesNotContain(teacher.accessCode())
                .doesNotContain(body(keeping).path("accessCode").asString())
                .doesNotContain(body(ending).path("accessCode").asString());
    }

    @Test
    void unlinkLeavesARecordWithoutAnyCode() throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.DELETE,
                        ADMIN + "/teachers/" + teacher.id() + "/telegram-link",
                        session);

        assertThat(response.getStatus()).isEqualTo(204);
        assertAdministratorRecord(onlyAuthorship("TEACHER_UNLINK_TELEGRAM"));
        assertThat(authorship()).hasSize(1);
        assertThat(authorshipDump()).doesNotContain(teacher.accessCode());
    }

    @Test
    void reissueOfAnInactiveOrMissingTeacherLeavesNoRecord() throws Exception {
        call(HttpMethod.POST, ADMIN + "/teachers/" + teacher.id() + "/inactivation", session);
        clearAuthorship();

        MockHttpServletResponse inactive =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/access-code",
                        session,
                        Map.of("endTelegramLink", false));
        MockHttpServletResponse missing =
                call(
                        HttpMethod.DELETE,
                        ADMIN + "/teachers/" + UUID.randomUUID() + "/telegram-link",
                        session);

        assertThat(inactive.getStatus()).isEqualTo(409);
        assertThat(errorCode(inactive)).isEqualTo("TEACHER_INACTIVE");
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(authorship()).isEmpty();
    }
}
