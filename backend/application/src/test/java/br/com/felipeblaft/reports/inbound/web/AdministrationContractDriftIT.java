package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;

/**
 * Deriva do contrato (FR-049, SC-005): toda resposta real da API de administração e de "Meus
 * alunos", com a pilha real e o Postgres real, é validada (com a requisição) contra {@code
 * api-administration.openapi.yaml}; toda rota do contrato é exercitada ao menos uma vez, com
 * sucesso e com recusa; e nenhuma resposta tem propriedade chamada {@code cpf}.
 */
class AdministrationContractDriftIT extends AdministrationStackBase {

    private static final String CONTRACT =
            "../../specs/005-admin-registrations-access/contracts/api-administration.openapi.yaml";
    private static final String API = "/api/v1";
    private static final OpenApiInteractionValidator VALIDATOR = validator();

    @Autowired BootstrapFirstAdministratorUseCase bootstrap;

    private final Set<String> exercised = new LinkedHashSet<>();
    private int responses;

    private static OpenApiInteractionValidator validator() {
        return OpenApiInteractionValidator.createForSpecificationUrl(
                        Path.of(CONTRACT).toAbsolutePath().normalize().toUri().toString())
                .withLevelResolver(
                        LevelResolver.create()
                                .withLevel(
                                        "validation.request.security.missing",
                                        ValidationReport.Level.IGNORE)
                                // allOf do contrato (AdministratorStudentInput): o validador trata
                                // cada parte como fechada
                                .withLevel(
                                        "validation.request.body.schema.additionalProperties",
                                        ValidationReport.Level.IGNORE)
                                .withLevel(
                                        "validation.request.parameter.header.missing",
                                        ValidationReport.Level.IGNORE)
                                .build())
                .build();
    }

    /**
     * Executa, valida requisição e resposta contra o contrato e varre o corpo atrás de {@code cpf}.
     */
    private MockHttpServletResponse exchange(
            HttpMethod method, String template, String path, Cookie cookie, Object body, int status)
            throws Exception {
        MockHttpServletRequestBuilder builder = request(method, API + path).with(csrf().asHeader());
        if (cookie != null) {
            builder.cookie(cookie);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String text ? text : JSON.writeValueAsString(body));
        }
        MvcResult result = mvc.perform(builder).andReturn();
        OpenApiValidationMatchers.openApi().isValid(VALIDATOR).match(result);
        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus())
                .as("%s %s: %s", method, path, response.getContentAsString())
                .isEqualTo(status);
        exercised.add(method + " " + template);
        responses++;
        String text = response.getContentAsString();
        if (!text.isBlank()
                && response.getContentType() != null
                && response.getContentType().contains("json")) {
            assertNoCpfProperty(JSON.readTree(text), method + " " + path);
        }
        return response;
    }

    private static void assertNoCpfProperty(JsonNode node, String where) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                assertThat(field.getKey().toLowerCase(Locale.ROOT))
                        .as("propriedade da resposta de %s", where)
                        .doesNotContain("cpf");
                assertNoCpfProperty(field.getValue(), where);
            }
        } else if (node.isArray()) {
            node.forEach(item -> assertNoCpfProperty(item, where));
        }
    }

    /** Pares {@code MÉTODO caminho} do contrato. */
    @SuppressWarnings("unchecked")
    private static Set<String> contractOperations() throws IOException {
        Map<String, Object> contract = new Yaml().load(Files.readString(Path.of(CONTRACT)));
        Map<String, Object> paths = (Map<String, Object>) contract.get("paths");
        Set<String> operations = new LinkedHashSet<>();
        for (Map.Entry<String, Object> path : paths.entrySet()) {
            for (String key : ((Map<String, Object>) path.getValue()).keySet()) {
                if (List.of("get", "post", "put", "patch", "delete").contains(key)) {
                    operations.add(key.toUpperCase(Locale.ROOT) + " " + path.getKey());
                }
            }
        }
        return operations;
    }

    @Test
    void everyRealResponseFollowsTheContractAndEveryRouteIsExercised() throws Exception {
        Cookie anonymous = null;
        // primeiro administrador com senha provisória (PUT /admin/password)
        bootstrap.run(
                Actor.system(ActionOrigin.SYSTEM_STARTUP),
                new BootstrapFirstAdministratorUseCase.Command(
                        "Primeiro Administrador",
                        "first.admin@example.test",
                        new Secret("provisional-password-long-1")));
        MockHttpServletResponse signedIn =
                exchange(
                        HttpMethod.POST,
                        "/admin/session",
                        "/admin/session",
                        anonymous,
                        Map.of(
                                "email",
                                "first.admin@example.test",
                                "password",
                                "provisional-password-long-1"),
                        200);
        Cookie restricted = new Cookie(ADMIN_COOKIE, signedIn.getCookie(ADMIN_COOKIE).getValue());
        exchange(
                HttpMethod.PUT,
                "/admin/password",
                "/admin/password",
                restricted,
                Map.of("newPassword", "provisional-password-long-1"),
                400);
        MockHttpServletResponse changed =
                exchange(
                        HttpMethod.PUT,
                        "/admin/password",
                        "/admin/password",
                        restricted,
                        Map.of("newPassword", "definitive-password-long-2"),
                        200);
        Cookie admin = new Cookie(ADMIN_COOKIE, changed.getCookie(ADMIN_COOKIE).getValue());
        exchange(HttpMethod.GET, "/admin/session", "/admin/session", admin, null, 200);
        exchange(
                HttpMethod.POST,
                "/admin/session",
                "/admin/session",
                anonymous,
                Map.of("email", "first.admin@example.test", "password", "wrong-password-any"),
                401);

        // professores
        MockHttpServletResponse createdTeacher =
                exchange(
                        HttpMethod.POST,
                        "/admin/teachers",
                        "/admin/teachers",
                        admin,
                        Map.of("name", "Clara Souza", "email", "clara@example.test"),
                        201);
        UUID clara = UUID.fromString(body(createdTeacher).path("teacher").path("id").asString());
        String claraCode = body(createdTeacher).path("accessCode").asString();
        exchange(
                HttpMethod.POST,
                "/admin/teachers",
                "/admin/teachers",
                admin,
                Map.of("name", "Outra", "email", "clara@example.test"),
                409);
        exchange(HttpMethod.GET, "/admin/teachers", "/admin/teachers", admin, null, 200);
        exchange(
                HttpMethod.GET,
                "/admin/teachers/{teacherId}",
                "/admin/teachers/" + clara,
                admin,
                null,
                200);
        exchange(
                HttpMethod.GET,
                "/admin/teachers/{teacherId}",
                "/admin/teachers/" + UUID.randomUUID(),
                admin,
                null,
                404);
        exchange(
                HttpMethod.PATCH,
                "/admin/teachers/{teacherId}",
                "/admin/teachers/" + clara,
                admin,
                Map.of("name", "Clara Souza Lima", "email", "clara@example.test"),
                200);
        sendText(USER_CLARA, claraCode);
        exchange(
                HttpMethod.POST,
                "/admin/teachers/{teacherId}/access-code",
                "/admin/teachers/" + clara + "/access-code",
                admin,
                Map.of("endTelegramLink", false),
                200);
        exchange(
                HttpMethod.DELETE,
                "/admin/teachers/{teacherId}/telegram-link",
                "/admin/teachers/" + clara + "/telegram-link",
                admin,
                null,
                204);
        exchange(
                HttpMethod.POST,
                "/admin/teachers/{teacherId}/inactivation",
                "/admin/teachers/" + clara + "/inactivation",
                admin,
                null,
                200);
        exchange(
                HttpMethod.POST,
                "/admin/teachers/{teacherId}/access-code",
                "/admin/teachers/" + clara + "/access-code",
                admin,
                Map.of("endTelegramLink", false),
                409);
        MockHttpServletResponse reactivated =
                exchange(
                        HttpMethod.POST,
                        "/admin/teachers/{teacherId}/reactivation",
                        "/admin/teachers/" + clara + "/reactivation",
                        admin,
                        null,
                        200);
        TeacherAccount bruno = createTeacher(admin, "Bruno Lima", "bruno@example.test");

        // administradores
        MockHttpServletResponse createdAdmin =
                exchange(
                        HttpMethod.POST,
                        "/admin/administrators",
                        "/admin/administrators",
                        admin,
                        Map.of("name", "Segundo Administrador", "email", "second@example.test"),
                        201);
        UUID second =
                UUID.fromString(body(createdAdmin).path("administrator").path("id").asString());
        exchange(
                HttpMethod.GET, "/admin/administrators", "/admin/administrators", admin, null, 200);
        exchange(
                HttpMethod.POST,
                "/admin/activation",
                "/admin/activation",
                anonymous,
                Map.of(
                        "email",
                        "second@example.test",
                        "activationCode",
                        "ZZZZZZZZZZZZ",
                        "newPassword",
                        "second-password-long-3"),
                401);
        exchange(
                HttpMethod.POST,
                "/admin/activation",
                "/admin/activation",
                anonymous,
                Map.of(
                        "email",
                        "second@example.test",
                        "activationCode",
                        body(createdAdmin).path("activationCode").asString(),
                        "newPassword",
                        "second-password-long-3"),
                204);
        exchange(
                HttpMethod.POST,
                "/admin/administrators/{administratorId}/inactivation",
                "/admin/administrators/" + second + "/inactivation",
                admin,
                null,
                200);
        exchange(
                HttpMethod.POST,
                "/admin/administrators/{administratorId}/reactivation",
                "/admin/administrators/" + second + "/reactivation",
                admin,
                null,
                200);

        // alunos do administrador e associações
        MockHttpServletResponse createdStudent =
                exchange(
                        HttpMethod.POST,
                        "/admin/students",
                        "/admin/students",
                        admin,
                        withTeachers(adultStudent("Ana Souza", CanaryCpf.MASKED), clara),
                        201);
        UUID student = UUID.fromString(body(createdStudent).path("id").asString());
        exchange(
                HttpMethod.POST,
                "/admin/students",
                "/admin/students",
                admin,
                adultStudent("Repetida", CanaryCpf.MASKED),
                409);
        exchange(HttpMethod.GET, "/admin/students", "/admin/students", admin, null, 200);
        exchange(
                HttpMethod.GET,
                "/admin/students/{studentId}",
                "/admin/students/" + student,
                admin,
                null,
                200);
        exchange(
                HttpMethod.PATCH,
                "/admin/students/{studentId}",
                "/admin/students/" + student,
                admin,
                Map.of("instrument", "Flauta"),
                200);
        exchange(
                HttpMethod.POST,
                "/admin/students/{studentId}/inactivation",
                "/admin/students/" + student + "/inactivation",
                admin,
                null,
                200);
        exchange(
                HttpMethod.POST,
                "/admin/students/{studentId}/reactivation",
                "/admin/students/" + student + "/reactivation",
                admin,
                null,
                200);
        String assignment = "/admin/students/" + student + "/teachers/" + bruno.id();
        String assignmentTemplate = "/admin/students/{studentId}/teachers/{teacherId}";
        exchange(HttpMethod.PUT, assignmentTemplate, assignment, admin, null, 204);
        exchange(
                HttpMethod.GET,
                assignmentTemplate + "/removal-preview",
                assignment + "/removal-preview",
                admin,
                null,
                200);
        exchange(HttpMethod.DELETE, assignmentTemplate, assignment, admin, null, 204);

        // "Meus alunos" do professor
        Cookie claraSession = signInTeacher(body(reactivated).path("accessCode").asString());
        MockHttpServletResponse mine =
                exchange(
                        HttpMethod.POST,
                        "/my-students",
                        "/my-students",
                        claraSession,
                        adultStudent("Davi Lima", "529.982.247-25"),
                        201);
        UUID own = UUID.fromString(body(mine).path("id").asString());
        exchange(
                HttpMethod.POST,
                "/my-students",
                "/my-students",
                claraSession,
                adultStudent("Repetido", "529.982.247-25"),
                409);
        exchange(HttpMethod.GET, "/my-students", "/my-students", claraSession, null, 200);
        exchange(
                HttpMethod.GET,
                "/my-students/{studentId}",
                "/my-students/" + own,
                claraSession,
                null,
                200);
        exchange(
                HttpMethod.GET,
                "/my-students/{studentId}",
                "/my-students/" + UUID.randomUUID(),
                claraSession,
                null,
                404);
        exchange(
                HttpMethod.PATCH,
                "/my-students/{studentId}",
                "/my-students/" + own,
                claraSession,
                Map.of("instrument", "Violão"),
                200);
        exchange(
                HttpMethod.POST,
                "/my-students/{studentId}/inactivation",
                "/my-students/" + own + "/inactivation",
                claraSession,
                null,
                200);
        exchange(
                HttpMethod.POST,
                "/my-students/{studentId}/reactivation",
                "/my-students/" + own + "/reactivation",
                claraSession,
                null,
                200);

        // recusas transversais: credencial do outro papel e sem credencial
        exchange(HttpMethod.GET, "/admin/teachers", "/admin/teachers", claraSession, null, 404);
        exchange(HttpMethod.GET, "/my-students", "/my-students", admin, null, 404);
        exchange(HttpMethod.GET, "/admin/students", "/admin/students", anonymous, null, 401);
        exchange(HttpMethod.GET, "/my-students", "/my-students", anonymous, null, 401);

        // sair
        exchange(HttpMethod.DELETE, "/admin/session", "/admin/session", admin, null, 204);

        assertThat(exercised)
                .as("toda rota do contrato foi exercitada")
                .containsAll(contractOperations());
        assertThat(responses).isGreaterThanOrEqualTo(40);
    }

    private static Map<String, Object> withTeachers(Map<String, Object> student, UUID... teachers) {
        Map<String, Object> complete = new java.util.LinkedHashMap<>(student);
        complete.put("teacherIds", List.of(teachers));
        return complete;
    }
}
