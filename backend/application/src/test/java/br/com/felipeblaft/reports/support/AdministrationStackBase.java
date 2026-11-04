package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Base dos testes transversais da feature 005 (fecho): a pilha <strong>real</strong> inteira, sem
 * nenhum dublê de caso de uso. A Web (administrador e professor) é exercida por MockMvc contra os
 * controllers e as cadeias de segurança reais; o Telegram, pelo simulador (API interna com o
 * executor em memória e a API de bots simulada); o estado vive no Postgres real. Reúne os
 * auxiliares de entrada, de chamada e de leitura do registro de autoria.
 */
public abstract class AdministrationStackBase extends TelegramConversationBase {

    protected static final String ADMIN_COOKIE = "admin_session";
    protected static final String TEACHER_COOKIE = "session";
    protected static final String ADMIN = "/api/v1/admin";

    @Autowired protected AdministratorRepository administrators;
    @Autowired protected PasswordHashPort hasher;
    @Autowired protected ChannelActionRepository channelActions;

    /** Um professor criado pelo administrador, com o código de acesso mostrado uma única vez. */
    protected record TeacherAccount(UUID id, String name, String email, String accessCode) {

        @Override
        public String toString() {
            return "TeacherAccount[id=" + id + "]";
        }
    }

    // ------------------------------------------------------------------ administrador

    protected AdministratorFixture insertAdministrator() {
        return AdministratorFixture.insertActive(administrators, hasher, clock.now());
    }

    protected AdministratorFixture insertAdministrator(String name, String email) {
        return AdministratorFixture.insertActive(administrators, hasher, clock.now(), name, email);
    }

    /** Entrada do administrador (corpo e origem de rede dados); devolve a resposta crua. */
    protected MockHttpServletResponse signInAdministratorRaw(
            String email, String password, String origin) throws Exception {
        return mvc.perform(
                        request(HttpMethod.POST, ADMIN + "/session")
                                .with(csrf().asHeader())
                                .with(
                                        r -> {
                                            r.setRemoteAddr(origin);
                                            return r;
                                        })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        JSON.writeValueAsString(
                                                Map.of("email", email, "password", password))))
                .andReturn()
                .getResponse();
    }

    protected Cookie signInAdministrator(AdministratorFixture fixture) throws Exception {
        return signInAdministrator(fixture, "198.51.100.10");
    }

    protected Cookie signInAdministrator(AdministratorFixture fixture, String origin)
            throws Exception {
        MockHttpServletResponse response =
                signInAdministratorRaw(fixture.email(), fixture.password().value(), origin);
        assertThat(response.getStatus()).as("entrada do administrador").isEqualTo(200);
        Cookie cookie = response.getCookie(ADMIN_COOKIE);
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    /** Chamada à API com o cookie dado e o token de CSRF; corpo opcional em JSON. */
    protected MockHttpServletResponse call(
            HttpMethod method, String path, Cookie cookie, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path).with(csrf().asHeader());
        if (cookie != null) {
            builder.cookie(cookie);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String text ? text : JSON.writeValueAsString(body));
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    protected MockHttpServletResponse call(HttpMethod method, String path, Cookie cookie)
            throws Exception {
        return call(method, path, cookie, null);
    }

    protected static JsonNode body(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    protected static String errorCode(MockHttpServletResponse response) throws Exception {
        return body(response).path("code").asString();
    }

    /** Cria o professor pela API do administrador (201) e guarda o código de acesso. */
    protected TeacherAccount createTeacher(Cookie administrator, String name, String email)
            throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers",
                        administrator,
                        Map.of("name", name, "email", email));
        assertThat(response.getStatus()).as("criação do professor").isEqualTo(201);
        JsonNode created = body(response);
        return new TeacherAccount(
                UUID.fromString(created.path("teacher").path("id").asString()),
                name,
                email,
                created.path("accessCode").asString());
    }

    // ------------------------------------------------------------------ professor

    /** Entrada do professor na Web com o código; devolve a resposta crua. */
    protected MockHttpServletResponse signInTeacherRaw(String code, String origin)
            throws Exception {
        return mvc.perform(
                        request(HttpMethod.POST, "/api/v1/session")
                                .with(csrf().asHeader())
                                .with(
                                        r -> {
                                            r.setRemoteAddr(origin);
                                            return r;
                                        })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(Map.of("code", code))))
                .andReturn()
                .getResponse();
    }

    protected Cookie signInTeacher(String code) throws Exception {
        MockHttpServletResponse response = signInTeacherRaw(code, "203.0.113.30");
        assertThat(response.getStatus()).as("entrada do professor").isEqualTo(200);
        Cookie cookie = response.getCookie(TEACHER_COOKIE);
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    // ------------------------------------------------------------------ alunos

    /** Corpo de cadastro de um aluno adulto (nascido em 1990), sem responsável. */
    protected static Map<String, Object> adultStudent(String name, String cpf) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("birthDate", "1990-03-04");
        body.put("cpf", cpf);
        body.put("instrument", "Piano");
        return body;
    }

    /** Corpo de cadastro de um menor com os três dados do responsável. */
    protected static Map<String, Object> minorStudent(
            String name,
            String birthDate,
            String cpf,
            String guardianName,
            String guardianPhone,
            String guardianEmail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("birthDate", birthDate);
        body.put("cpf", cpf);
        body.put("instrument", "Violão");
        body.put(
                "guardian",
                Map.of("name", guardianName, "phone", guardianPhone, "email", guardianEmail));
        return body;
    }

    /** O professor cadastra o próprio aluno pela Web (201) e devolve o identificador. */
    protected UUID registerMyStudent(Cookie teacher, Map<String, Object> input) throws Exception {
        Map<String, Object> body = withDefaultConsent(input);
        MockHttpServletResponse response =
                call(HttpMethod.POST, "/api/v1/my-students", teacher, body);
        assertThat(response.getStatus()).as("cadastro pelo professor").isEqualTo(201);
        return UUID.fromString(body(response).path("id").asString());
    }

    /**
     * Aluno menor sem consentimento no corpo recebe um consentimento válido do termo 1 (feature 007,
     * FR-030), criando o termo se ainda não existe; adulto e corpo que já traz o consentimento passam
     * como vieram.
     */
    protected Map<String, Object> withDefaultConsent(Map<String, Object> body) {
        if (body.containsKey("consent") || !(body.get("birthDate") instanceof String text)) {
            return body;
        }
        java.time.LocalDate today =
                java.time.LocalDate.ofInstant(
                        clock.now(), java.time.ZoneId.of("America/Sao_Paulo"));
        if (java.time.Period.between(java.time.LocalDate.parse(text), today).getYears() >= 18) {
            return body;
        }
        jdbc.update(
                "insert into consent_term_version (id, number, published_on, text,"
                        + " requires_new_consent, created_by, created_at)"
                        + " values (?, 1, date '2025-01-01', 'Institutional text', false, ?, now())"
                        + " on conflict (number) do nothing",
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID());
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put(
                "consent",
                Map.of(
                        "consentingName", "Responsável Fictício",
                        "relationship", "MOTHER",
                        "consentedOn", "2025-01-02",
                        "termVersion", 1,
                        "collectionMethod", "SIGNED_PAPER"));
        return copy;
    }

    /** O administrador cadastra o aluno (201), associado aos professores dados. */
    protected UUID registerStudentAsAdministrator(
            Cookie administrator, Map<String, Object> body, UUID... teacherIds) throws Exception {
        Map<String, Object> complete = new LinkedHashMap<>(withDefaultConsent(body));
        complete.put("teacherIds", List.of(teacherIds));
        MockHttpServletResponse response =
                call(HttpMethod.POST, ADMIN + "/students", administrator, complete);
        assertThat(response.getStatus()).as("cadastro pelo administrador").isEqualTo(201);
        return UUID.fromString(body(response).path("id").asString());
    }

    // ------------------------------------------------------------------ Telegram

    /** Ação pendente de envio ao chat (some quando o vínculo termina, FR-031). */
    protected void insertPendingAction(String id, long chatId, UUID teacherId) {
        RetryPolicy policy =
                new RetryPolicy(
                        Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));
        channelActions.saveBatch(
                List.of(
                        ChannelAction.create(
                                id,
                                "batch:" + id,
                                ChannelActionOrigin.PROACTIVE,
                                null,
                                chatId,
                                teacherId,
                                null,
                                ChannelActionType.MESSAGE,
                                "{\"text\":\"" + id + "\"}",
                                null,
                                clock.now(),
                                policy)));
    }

    protected String stateOfAction(String id) {
        return jdbc.queryForObject(
                "select state from channel_action where id = ?", String.class, id);
    }

    protected int telegramLinks() {
        return jdbc.queryForObject("select count(*) from telegram_link", Integer.class);
    }

    /** O texto do bot só orienta o envio do código de acesso (conta sem vínculo). */
    protected static boolean asksForTheAccessCode(String text) {
        return text.startsWith("Olá! Para usar o bot, envie o código de acesso");
    }

    /** Envia o texto como o professor e devolve o texto da última mensagem do bot. */
    protected String botReplyTo(long user, String text) throws Exception {
        API_BOTS.resetRequests();
        sendText(user, text);
        return lastMessage().get("text").asString();
    }

    // ------------------------------------------------------------------ registro de autoria

    /** Esvazia o registro de autoria (só o teste, dono da tabela, suspende os gatilhos). */
    protected void clearAuthorship() {
        jdbc.execute(
                "alter table authorship_record disable trigger user;"
                        + " truncate table authorship_record;"
                        + " alter table authorship_record enable trigger user");
    }

    private static final String AUTHORSHIP_COLUMNS =
            "id, occurred_at, author_id, author_role, origin, operation, target_type, target_id,"
                    + " result, error_code, origin_hash, correlation_id";

    /** Todos os registros, na ordem de gravação. */
    protected List<Map<String, Object>> authorship() {
        return jdbc.queryForList(
                "select "
                        + AUTHORSHIP_COLUMNS
                        + " from authorship_record order by occurred_at, ctid");
    }

    /** Os registros da operação (ou evento), na ordem de gravação. */
    protected List<Map<String, Object>> authorship(String operation) {
        return jdbc.queryForList(
                "select "
                        + AUTHORSHIP_COLUMNS
                        + " from authorship_record where operation = ?"
                        + " order by occurred_at, ctid",
                operation);
    }

    /** O registro único da operação. */
    protected Map<String, Object> onlyAuthorship(String operation) {
        List<Map<String, Object>> rows = authorship(operation);
        assertThat(rows).as("registros de autoria de " + operation).hasSize(1);
        return rows.getFirst();
    }

    /** Todo o conteúdo da tabela de autoria como texto, para varrer valores proibidos. */
    protected String authorshipDump() {
        StringBuilder all = new StringBuilder();
        for (Map<String, Object> row : authorship()) {
            row.values().forEach(value -> all.append(value).append('|'));
            all.append('\n');
        }
        return all.toString();
    }

    /** Confere papel, canal e resultado de um registro. */
    protected static void assertRecord(
            Map<String, Object> row, String role, String origin, String result) {
        assertThat(row.get("author_role")).as("role").isEqualTo(role);
        assertThat(row.get("origin")).as("channel").isEqualTo(origin);
        assertThat(row.get("result")).as("result").isEqualTo(result);
    }
}
