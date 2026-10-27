package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.ContractApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Ponta a ponta com Postgres real e os casos de uso reais (nenhum dublê): o administrador entra por
 * {@code /api/v1/admin/session}, cria o professor, o professor entra pelo código e cadastra, lista
 * e muda o estado do próprio aluno por {@code /api/v1/my-students}; o aluno de outro professor
 * responde 404, igual ao aluno que não existe (FR-024, FR-025). O CPF nunca aparece nas respostas.
 */
class RegistryEndToEndIT extends ContractApiBase {

    private static final String CPF_DIGITS = "52998224725";
    private static final String NEW_STUDENT =
            "{\"name\":\"Helena Prado\",\"birthDate\":\"1990-03-04\",\"cpf\":\"529.982.247-25\","
                    + "\"instrument\":\"Piano\"}";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired AdministratorRepository administrators;
    @Autowired PasswordHashPort hasher;

    private Cookie administrator;

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        AdministratorFixture fixture =
                AdministratorFixture.insertActive(administrators, hasher, clock.now());
        MvcResult result =
                mvc.perform(
                                post("/api/v1/admin/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + fixture.email()
                                                        + "\",\"password\":\""
                                                        + fixture.password().value()
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("admin_session");
        assertThat(cookie).isNotNull();
        administrator = new Cookie(cookie.getName(), cookie.getValue());
    }

    /** O administrador cria o professor e devolve o código de acesso (só nesta resposta). */
    private String createTeacher(String name, String email) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/admin/teachers")
                                        .cookie(administrator)
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\""
                                                        + name
                                                        + "\",\"email\":\""
                                                        + email
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json(result).path("accessCode").asText();
    }

    private Cookie teacherSession(String name, String email) throws Exception {
        return signIn(createTeacher(name, email));
    }

    private String registerStudent(Cookie teacher) throws Exception {
        MvcResult result =
                mvc.perform(
                                authenticated(post("/api/v1/my-students"), teacher)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(NEW_STUDENT))
                        .andExpect(status().isCreated())
                        .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(CPF_DIGITS);
        return json(result).path("id").asText();
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void theTeacherRegistersAndListsOnlyItsOwnStudent() throws Exception {
        Cookie clara = teacherSession("Clara Souza", "clara@example.test");
        Cookie bruno = teacherSession("Bruno Lima", "bruno@example.test");

        String id = registerStudent(clara);

        MvcResult own =
                mvc.perform(get("/api/v1/my-students").cookie(clara))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(1))
                        .andExpect(jsonPath("$.items[0].id").value(id))
                        .andExpect(jsonPath("$.items[0].name").value("Helena Prado"))
                        .andReturn();
        assertThat(own.getResponse().getContentAsString()).doesNotContain(CPF_DIGITS);
        mvc.perform(get("/api/v1/my-students/" + id).cookie(clara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/my-students").cookie(bruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void anotherTeachersStudentAnswersExactlyLikeOneThatDoesNotExist() throws Exception {
        Cookie clara = teacherSession("Clara Souza", "clara@example.test");
        Cookie bruno = teacherSession("Bruno Lima", "bruno@example.test");
        String id = registerStudent(clara);
        String missing = "00000000-0000-4000-8000-000000000999";

        String foreign =
                mvc.perform(get("/api/v1/my-students/" + id).cookie(bruno))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String absent =
                mvc.perform(get("/api/v1/my-students/" + missing).cookie(bruno))
                        .andExpect(status().isNotFound())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(foreign).isEqualTo(absent);
        mvc.perform(
                        authenticated(patch("/api/v1/my-students/" + id), bruno)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Nome Trocado\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(authenticated(post("/api/v1/my-students/" + id + "/inactivation"), bruno))
                .andExpect(status().isNotFound());
        // o aluno de Clara segue intacto
        mvc.perform(get("/api/v1/my-students/" + id).cookie(clara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Helena Prado"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void theTeacherInactivatesAndReactivatesItsOwnStudent() throws Exception {
        Cookie clara = teacherSession("Clara Souza", "clara@example.test");
        String id = registerStudent(clara);

        mvc.perform(authenticated(post("/api/v1/my-students/" + id + "/inactivation"), clara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/my-students/" + id).cookie(clara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(authenticated(post("/api/v1/my-students/" + id + "/reactivation"), clara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
