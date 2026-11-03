package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * SC-014: cada operação da lista do FR-040 ({@code data-model.md} §6), ao ser executada na pilha
 * real, deixa um registro com papel, canal e resultado corretos e sem CPF nem dado do responsável.
 * A lista esperada é conferida contra o próprio {@code data-model.md}: operação nova na seção 6 sem
 * cenário aqui reprova o teste. Leituras e expiração de sessão não geram registro.
 */
class AuthorshipCoverageIT extends AdministrationStackBase {

    private static final String ADMIN_ROLE = "ADMINISTRATOR";
    private static final String TEACHER_ROLE = "TEACHER";
    private static final String BOOTSTRAP_EMAIL = "first.admin@example.test";
    private static final String BOOTSTRAP_PASSWORD = "provisional-password-long-1";
    private static final String NEW_PASSWORD = "definitive-password-long-2";
    private static final String SECOND_EMAIL = "second.admin@example.test";
    private static final String SECOND_PASSWORD = "second-password-long-3";

    /** Operação, papel, canal e resultado esperados do registro. */
    private record Expectation(String role, String origin, String result) {}

    private static final Map<String, Expectation> EXPECTED = expectations();

    private static Map<String, Expectation> expectations() {
        Map<String, Expectation> expected = new LinkedHashMap<>();
        for (String operation :
                List.of(
                        "TEACHER_REGISTER",
                        "TEACHER_UPDATE",
                        "TEACHER_INACTIVATE",
                        "TEACHER_REACTIVATE",
                        "TEACHER_REISSUE_ACCESS_CODE",
                        "TEACHER_UNLINK_TELEGRAM",
                        "ADMINISTRATOR_CREATE",
                        "ADMINISTRATOR_INACTIVATE",
                        "ADMINISTRATOR_REACTIVATE",
                        "ADMIN_ACTIVATE",
                        "ADMIN_CHANGE_PASSWORD",
                        "ASSIGNMENT_ASSIGN",
                        "ASSIGNMENT_REMOVE",
                        "STUDENT_REGISTER",
                        "STUDENT_UPDATE",
                        "STUDENT_INACTIVATE",
                        "STUDENT_REACTIVATE",
                        "ADMIN_SIGN_IN",
                        "ADMIN_SIGN_OUT")) {
            expected.put(operation, new Expectation(ADMIN_ROLE, "WEB", "SUCCESS"));
        }
        expected.put(
                "FIRST_ADMINISTRATOR_BOOTSTRAP",
                new Expectation(null, "SYSTEM_STARTUP", "SUCCESS"));
        expected.put("TEACHER_SIGN_IN", new Expectation(TEACHER_ROLE, "WEB", "SUCCESS"));
        expected.put("TEACHER_LINK_TELEGRAM", new Expectation(TEACHER_ROLE, "TELEGRAM", "SUCCESS"));
        expected.put("ATTEMPT_BLOCKED", new Expectation(null, "WEB", "BLOCKED"));
        expected.put("AUTHORIZATION_DENIED", new Expectation(TEACHER_ROLE, "WEB", "DENIED"));
        expected.put("CPF_REFUSED_OTHER_STUDENT", new Expectation(TEACHER_ROLE, "WEB", "DENIED"));
        return expected;
    }

    @Autowired BootstrapFirstAdministratorUseCase bootstrap;

    /** Os nomes em maiúsculas citados na seção 6 do {@code data-model.md}. */
    private static Set<String> operationsOfTheDataModel() throws Exception {
        Path file = Path.of("../../specs/005-admin-registrations-access/data-model.md");
        String text = Files.readString(file);
        int start = text.indexOf("## 6.");
        int end = text.indexOf("## 7.");
        assertThat(start).isPositive();
        assertThat(end).isGreaterThan(start);
        Set<String> names = new LinkedHashSet<>();
        Matcher token = Pattern.compile("`([A-Z][A-Z_]+)`").matcher(text.substring(start, end));
        while (token.find()) {
            names.add(token.group(1));
        }
        return names;
    }

    @Test
    void theScenarioCoversEveryOperationListedInTheDataModel() throws Exception {
        assertThat(operationsOfTheDataModel())
                .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
    }

    @Test
    void everyOperationOfTheListLeavesARecordWithRoleChannelAndResultAndNoPersonalData()
            throws Exception {
        // 1. criação do primeiro administrador (sistema) e troca da senha provisória
        bootstrap.run(
                Actor.system(ActionOrigin.SYSTEM_STARTUP),
                new BootstrapFirstAdministratorUseCase.Command(
                        "Primeiro Administrador", BOOTSTRAP_EMAIL, new Secret(BOOTSTRAP_PASSWORD)));
        MockHttpServletResponse provisional =
                signInAdministratorRaw(BOOTSTRAP_EMAIL, BOOTSTRAP_PASSWORD, "198.51.100.80");
        assertThat(provisional.getStatus()).isEqualTo(200);
        Cookie restricted =
                new Cookie(ADMIN_COOKIE, provisional.getCookie(ADMIN_COOKIE).getValue());
        MockHttpServletResponse changed =
                call(
                        HttpMethod.PUT,
                        ADMIN + "/password",
                        restricted,
                        Map.of("newPassword", NEW_PASSWORD));
        assertThat(changed.getStatus()).isEqualTo(200);
        Cookie admin = new Cookie(ADMIN_COOKIE, changed.getCookie(ADMIN_COOKIE).getValue());

        // 2. professores: cadastro, edição, vínculo, reemissão, desvinculação, inativação
        TeacherAccount clara = createTeacher(admin, "Clara Souza", "clara@example.test");
        TeacherAccount bruno = createTeacher(admin, "Bruno Lima", "bruno@example.test");
        assertOk(
                call(
                        HttpMethod.PATCH,
                        ADMIN + "/teachers/" + clara.id(),
                        admin,
                        Map.of("name", "Clara Souza Lima", "email", "clara@example.test")));
        sendText(USER_CLARA, clara.accessCode());
        assertOk(
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + clara.id() + "/access-code",
                        admin,
                        Map.of("endTelegramLink", false)));
        assertThat(
                        call(
                                        HttpMethod.DELETE,
                                        ADMIN + "/teachers/" + clara.id() + "/telegram-link",
                                        admin)
                                .getStatus())
                .isEqualTo(204);
        assertOk(call(HttpMethod.POST, ADMIN + "/teachers/" + clara.id() + "/inactivation", admin));
        MockHttpServletResponse reactivated =
                call(HttpMethod.POST, ADMIN + "/teachers/" + clara.id() + "/reactivation", admin);
        assertOk(reactivated);
        Cookie claraSession = signInTeacher(body(reactivated).path("accessCode").asString());
        Cookie brunoSession = signInTeacher(bruno.accessCode());

        // 3. administradores: criação, ativação, inativação, reativação
        MockHttpServletResponse created =
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators",
                        admin,
                        Map.of("name", "Segundo Administrador", "email", SECOND_EMAIL));
        assertThat(created.getStatus()).isEqualTo(201);
        UUID second = UUID.fromString(body(created).path("administrator").path("id").asString());
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        ADMIN + "/activation",
                                        null,
                                        Map.of(
                                                "email",
                                                SECOND_EMAIL,
                                                "activationCode",
                                                body(created).path("activationCode").asString(),
                                                "newPassword",
                                                SECOND_PASSWORD))
                                .getStatus())
                .isEqualTo(204);
        assertOk(
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators/" + second + "/inactivation",
                        admin));
        assertOk(
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators/" + second + "/reactivation",
                        admin));

        // 4. aluno canário (menor, com responsável): cadastro, edição, situação, associações
        UUID student =
                registerStudentAsAdministrator(
                        admin,
                        minorStudent(
                                CanaryStudent.NAME,
                                CanaryStudent.BIRTH_DATE_ISO,
                                CanaryCpf.MASKED,
                                CanaryStudent.GUARDIAN_NAME,
                                CanaryStudent.GUARDIAN_PHONE_MASKED,
                                CanaryStudent.GUARDIAN_EMAIL),
                        clara.id());
        assertOk(
                call(
                        HttpMethod.PATCH,
                        ADMIN + "/students/" + student,
                        admin,
                        Map.of("instrument", "Flauta")));
        assertOk(call(HttpMethod.POST, ADMIN + "/students/" + student + "/inactivation", admin));
        assertOk(call(HttpMethod.POST, ADMIN + "/students/" + student + "/reactivation", admin));
        String assignment = ADMIN + "/students/" + student + "/teachers/" + bruno.id();
        assertThat(call(HttpMethod.PUT, assignment, admin).getStatus()).isEqualTo(204);
        assertThat(call(HttpMethod.DELETE, assignment, admin).getStatus()).isEqualTo(204);

        // 5. negativas: aluno alheio por identificador e CPF de aluno de outro professor
        assertThat(call(HttpMethod.GET, "/api/v1/my-students/" + student, brunoSession).getStatus())
                .isEqualTo(404);
        MockHttpServletResponse refused =
                call(
                        HttpMethod.POST,
                        "/api/v1/my-students",
                        brunoSession,
                        adultStudent("Outro Aluno", CanaryCpf.MASKED));
        assertThat(errorCode(refused)).isEqualTo("CPF_UNAVAILABLE");

        // 6. bloqueio por tentativas e "Sair"
        for (int i = 0; i < 5; i++) {
            signInTeacherRaw("ZZZZZZZZ", "203.0.113.90");
        }
        assertThat(signInTeacherRaw(clara.accessCode(), "203.0.113.90").getStatus()).isEqualTo(429);
        assertThat(call(HttpMethod.DELETE, ADMIN + "/session", admin).getStatus()).isEqualTo(204);

        // cada operação da lista deixou o registro certo
        for (Map.Entry<String, Expectation> entry : EXPECTED.entrySet()) {
            List<Map<String, Object>> rows = authorship(entry.getKey());
            assertThat(rows).as("registros de %s", entry.getKey()).isNotEmpty();
            Expectation expectation = entry.getValue();
            assertThat(rows)
                    .as("registro de %s com papel, canal e resultado esperados", entry.getKey())
                    .anyMatch(
                            row ->
                                    java.util.Objects.equals(
                                                    row.get("author_role"), expectation.role())
                                            && row.get("origin").equals(expectation.origin())
                                            && row.get("result").equals(expectation.result()));
        }
        // nenhum CPF nem dado do aluno ou do responsável em nenhum registro
        ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf())
                .assertClean(Place.AUTHORSHIP, authorshipDump());
        assertThat(authorshipDump())
                .doesNotContain(BOOTSTRAP_PASSWORD)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(SECOND_PASSWORD)
                .doesNotContain(clara.accessCode())
                .doesNotContain(bruno.accessCode());
        assertThat(claraSession).isNotNull();
    }

    @Test
    void readsAndSessionExpiryLeaveNoRecord() throws Exception {
        Cookie admin = signInAdministrator(insertAdministrator());
        TeacherAccount clara = createTeacher(admin, "Clara Souza", "clara@example.test");
        Cookie claraSession = signInTeacher(clara.accessCode());
        UUID student =
                registerStudentAsAdministrator(
                        admin, adultStudent("Ana Souza", CanaryCpf.MASKED), clara.id());
        int before = authorship().size();

        List<String> adminReads =
                List.of(
                        "/session",
                        "/teachers",
                        "/teachers/" + clara.id(),
                        "/administrators",
                        "/students",
                        "/students/" + student,
                        "/students/" + student + "/teachers/" + clara.id() + "/removal-preview");
        for (String path : adminReads) {
            assertThat(call(HttpMethod.GET, ADMIN + path, admin).getStatus())
                    .as(path)
                    .isEqualTo(200);
        }
        assertThat(call(HttpMethod.GET, "/api/v1/my-students", claraSession).getStatus())
                .isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/v1/my-students/" + student, claraSession).getStatus())
                .isEqualTo(200);
        clock.advance(Duration.ofHours(3));
        assertThat(call(HttpMethod.GET, ADMIN + "/session", admin).getStatus()).isEqualTo(401);

        assertThat(authorship()).hasSize(before);
    }

    private static void assertOk(MockHttpServletResponse response) {
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
