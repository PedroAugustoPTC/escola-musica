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
 * Registro de autoria da gestão de professores (FR-040, parte do SC-014): cadastrar, editar,
 * inativar e reativar deixam registro do administrador, no canal Web, com o professor como alvo e o
 * resultado; nunca o código de acesso nem o e-mail digitado. Operação recusada não deixa registro
 * de sucesso.
 */
class AuthorshipTeacherOperationsIT extends AdministrationStackBase {

    private static final String EMAIL = "clara.souza@example.test";

    private AdministratorFixture administrator;
    private Cookie session;

    @BeforeEach
    void prepare() throws Exception {
        administrator = insertAdministrator();
        session = signInAdministrator(administrator);
        clearAuthorship();
    }

    private void assertAdministratorRecord(Map<String, Object> record, UUID teacherId) {
        assertRecord(record, "ADMINISTRATOR", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(administrator.administrator().id());
        assertThat(record.get("target_type")).isEqualTo("TEACHER");
        assertThat(record.get("target_id")).isEqualTo(teacherId);
        assertThat(record.get("error_code")).isNull();
        assertThat(record.get("origin_hash")).isNull();
        assertThat(UUID.fromString((String) record.get("correlation_id"))).isNotNull();
    }

    @Test
    void registerUpdateInactivateAndReactivateEachLeaveARecordWithoutTheCode() throws Exception {
        TeacherAccount teacher = createTeacher(session, "Clara Souza", EMAIL);
        MockHttpServletResponse update =
                call(
                        HttpMethod.PATCH,
                        ADMIN + "/teachers/" + teacher.id(),
                        session,
                        Map.of("name", "Clara Souza Lima", "email", "clara.lima@example.test"));
        assertThat(update.getStatus()).isEqualTo(200);
        MockHttpServletResponse inactivation =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/inactivation",
                        session);
        assertThat(inactivation.getStatus()).isEqualTo(200);
        MockHttpServletResponse reactivation =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/reactivation",
                        session);
        assertThat(reactivation.getStatus()).isEqualTo(200);

        for (String operation :
                List.of(
                        "TEACHER_REGISTER",
                        "TEACHER_UPDATE",
                        "TEACHER_INACTIVATE",
                        "TEACHER_REACTIVATE")) {
            assertAdministratorRecord(onlyAuthorship(operation), teacher.id());
        }
        assertThat(authorship()).hasSize(4);
        String reactivationCode = body(reactivation).path("accessCode").asString();
        assertThat(authorshipDump())
                .doesNotContain(teacher.accessCode())
                .doesNotContain(reactivationCode)
                .doesNotContain(EMAIL)
                .doesNotContain("clara.lima");
    }

    @Test
    void refusedOperationsLeaveNoSuccessRecord() throws Exception {
        createTeacher(session, "Clara Souza", EMAIL);
        clearAuthorship();

        MockHttpServletResponse duplicate =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers",
                        session,
                        Map.of("name", "Outra Clara", "email", EMAIL.toUpperCase()));
        MockHttpServletResponse invalid =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers",
                        session,
                        Map.of("name", "Sem Email", "email", "isto-nao-e-email"));
        MockHttpServletResponse missing =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + UUID.randomUUID() + "/inactivation",
                        session);

        assertThat(duplicate.getStatus()).isEqualTo(409);
        assertThat(errorCode(duplicate)).isEqualTo("DUPLICATE_EMAIL");
        assertThat(invalid.getStatus()).isEqualTo(400);
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(authorship()).isEmpty();
    }

    @Test
    void aTeacherCredentialOnTheTeacherAdministrationLeavesNoTeacherOperationRecord()
            throws Exception {
        TeacherAccount teacher = createTeacher(session, "Clara Souza", EMAIL);
        Cookie teacherSession = signInTeacher(teacher.accessCode());
        clearAuthorship();

        MockHttpServletResponse response =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers",
                        teacherSession,
                        Map.of("name", "Intrusa", "email", "intrusa@example.test"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from teacher", Integer.class)).isEqualTo(1);
        assertThat(authorship().stream().map(row -> row.get("operation")))
                .noneMatch(operation -> String.valueOf(operation).startsWith("TEACHER_REGISTER"));
    }
}
