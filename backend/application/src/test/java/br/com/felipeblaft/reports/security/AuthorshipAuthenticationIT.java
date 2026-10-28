package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.CanaryCpf;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Registro de autoria das autenticações do administrador e das negativas de autorização (FR-040,
 * parte do SC-014), com a pilha real: entrada com sucesso, falha e bloqueio, "Sair" e aluno de
 * outro professor deixam registro com papel, canal e resultado corretos; o e-mail digitado de uma
 * conta que não existe nunca é gravado (só "e-mail desconhecido" e o resumo da origem).
 */
class AuthorshipAuthenticationIT extends AdministrationStackBase {

    private static final String ORIGIN = "198.51.100.40";
    private static final String UNKNOWN_EMAIL = "ghost.missing@example.test";
    private static final String HEX64 = "[0-9a-f]{64}";

    private AdministratorFixture administrator;

    @BeforeEach
    void prepare() {
        administrator = insertAdministrator();
    }

    @Test
    void successfulSignInLeavesARecordWithRoleChannelAndResult() throws Exception {
        signInAdministrator(administrator, ORIGIN);

        Map<String, Object> record = onlyAuthorship("ADMIN_SIGN_IN");
        assertRecord(record, "ADMINISTRATOR", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(administrator.administrator().id());
        assertThat(record.get("error_code")).isNull();
        assertThat((String) record.get("origin_hash")).matches(HEX64);
        assertThat(record.get("target_id")).isNull();
        assertThat(UUID.fromString((String) record.get("correlation_id"))).isNotNull();
    }

    @Test
    void wrongPasswordOfAKnownAccountIsRecordedAsFailureOfThatAdministrator() throws Exception {
        MockHttpServletResponse response =
                signInAdministratorRaw(administrator.email(), "password-failed-123", ORIGIN);

        assertThat(response.getStatus()).isEqualTo(401);
        Map<String, Object> record = onlyAuthorship("ADMIN_SIGN_IN");
        assertRecord(record, "ADMINISTRATOR", "WEB", "FAILURE");
        assertThat(record.get("author_id")).isEqualTo(administrator.administrator().id());
        assertThat(record.get("error_code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(authorshipDump()).doesNotContain("password-failed-123");
    }

    @Test
    void unknownEmailRecordsUnknownEmailAndTheOriginNeverTheTypedAddress() throws Exception {
        MockHttpServletResponse response =
                signInAdministratorRaw(UNKNOWN_EMAIL, "any-password-123", ORIGIN);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(errorCode(response)).isEqualTo("INVALID_CREDENTIALS");
        Map<String, Object> record = onlyAuthorship("ADMIN_SIGN_IN");
        assertRecord(record, null, "WEB", "FAILURE");
        assertThat(record.get("author_id")).isNull();
        assertThat(record.get("error_code")).isEqualTo("UNKNOWN_EMAIL");
        assertThat((String) record.get("origin_hash")).matches(HEX64);
        String dump = authorshipDump();
        assertThat(dump)
                .doesNotContain(UNKNOWN_EMAIL)
                .doesNotContain("ghost")
                .doesNotContain("any-password")
                .doesNotContain(ORIGIN);
    }

    @Test
    void attemptsRefusedByTheBlockAreRecordedAsBlockedWithoutRole() throws Exception {
        for (int i = 0; i < 5; i++) {
            signInAdministratorRaw(administrator.email(), "errada-" + i + "-password", ORIGIN);
        }

        MockHttpServletResponse blocked =
                signInAdministratorRaw(
                        administrator.email(), administrator.password().value(), ORIGIN);

        assertThat(blocked.getStatus()).isEqualTo(429);
        List<Map<String, Object>> records = authorship("ADMIN_SIGN_IN");
        assertThat(records).hasSize(6);
        Map<String, Object> last = records.getLast();
        assertRecord(last, null, "WEB", "BLOCKED");
        assertThat(last.get("author_id")).isNull();
        assertThat(last.get("error_code")).isEqualTo("TEMPORARILY_BLOCKED");
        assertThat((String) last.get("origin_hash")).matches(HEX64);
        assertThat(authorshipDump()).doesNotContain(administrator.password().value());
    }

    @Test
    void signOutLeavesARecordOfTheAdministrator() throws Exception {
        Cookie session = signInAdministrator(administrator, ORIGIN);

        MockHttpServletResponse response = call(HttpMethod.DELETE, ADMIN + "/session", session);

        assertThat(response.getStatus()).isEqualTo(204);
        Map<String, Object> record = onlyAuthorship("ADMIN_SIGN_OUT");
        assertRecord(record, "ADMINISTRATOR", "WEB", "SUCCESS");
        assertThat(record.get("author_id")).isEqualTo(administrator.administrator().id());
        // a sessão encerrada não vale mais
        assertThat(call(HttpMethod.GET, ADMIN + "/session", session).getStatus()).isEqualTo(401);
    }

    @Test
    void anotherTeachersStudentAndAMissingOneLeaveTheSameDenialRecord() throws Exception {
        Cookie admin = signInAdministrator(administrator, ORIGIN);
        TeacherAccount owner = createTeacher(admin, "Clara Souza", "clara@example.test");
        TeacherAccount intruder = createTeacher(admin, "Bruno Lima", "bruno@example.test");
        Cookie ownerSession = signInTeacher(owner.accessCode());
        Cookie intruderSession = signInTeacher(intruder.accessCode());
        UUID student =
                registerMyStudent(ownerSession, adultStudent("Helena Prado", CanaryCpf.MASKED));
        UUID missing = UUID.fromString("00000000-0000-4000-8000-000000000999");

        MockHttpServletResponse foreign =
                call(HttpMethod.GET, "/api/v1/my-students/" + student, intruderSession);
        MockHttpServletResponse absent =
                call(HttpMethod.GET, "/api/v1/my-students/" + missing, intruderSession);

        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(foreign.getContentAsString()).isEqualTo(absent.getContentAsString());
        List<Map<String, Object>> denials = authorship("AUTHORIZATION_DENIED");
        assertThat(denials).hasSize(2);
        for (Map<String, Object> denial : denials) {
            assertRecord(denial, "TEACHER", "WEB", "DENIED");
            assertThat(denial.get("author_id")).isEqualTo(intruder.id());
            assertThat(denial.get("target_type")).isEqualTo("STUDENT");
            assertThat(denial.get("error_code")).isEqualTo("RESOURCE_NOT_FOUND");
        }
        // o alvo é o identificador pedido, igual para o alheio e para o inexistente (FR-025)
        assertThat(denials)
                .extracting(row -> row.get("target_id"))
                .containsExactly(student, missing);
        assertThat(authorshipDump()).doesNotContain(CanaryCpf.DIGITS).doesNotContain("Helena");
    }

    @Test
    void authorizedReadsAndExpiredSessionsLeaveNoRecord() throws Exception {
        Cookie admin = signInAdministrator(administrator, ORIGIN);
        TeacherAccount teacher = createTeacher(admin, "Clara Souza", "clara@example.test");
        Cookie session = signInTeacher(teacher.accessCode());
        UUID student = registerMyStudent(session, adultStudent("Helena Prado", CanaryCpf.MASKED));
        int before = authorship().size();

        assertThat(call(HttpMethod.GET, "/api/v1/my-students", session).getStatus()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/v1/my-students/" + student, session).getStatus())
                .isEqualTo(200);
        assertThat(call(HttpMethod.GET, ADMIN + "/students", admin).getStatus()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, ADMIN + "/teachers", admin).getStatus()).isEqualTo(200);
        clock.advance(java.time.Duration.ofHours(3));
        assertThat(call(HttpMethod.GET, ADMIN + "/students", admin).getStatus()).isEqualTo(401);

        assertThat(authorship()).hasSize(before);
    }
}
