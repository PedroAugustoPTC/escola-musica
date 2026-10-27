package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Base dos testes transversais de privacidade e segurança da feature 005: a pilha REAL (Postgres,
 * casos de uso, controladores, fluxo do Telegram e adaptadores; nenhum dublê de caso de uso), com a
 * captura de logs ligada em todo teste e atalhos para o administrador, o professor e o canal do
 * Telegram. Todo teste que herda desta classe compartilha um só contexto Spring.
 */
public abstract class RealStackBase extends TelegramConversationBase {

    public static final String ADMIN_COOKIE = "admin_session";
    public static final String ADMIN_BASE = "/api/v1/admin";

    @Autowired protected AdministratorRepository administrators;
    @Autowired protected PasswordHashPort hasher;
    @Autowired protected BootstrapFirstAdministratorUseCase bootstrapAdministrator;

    /** Eventos de log do teste (aplicação em DEBUG, o restante em INFO). */
    protected LogCapture logs;

    /** Respostas do canal interno do Telegram (JSON de ações), na ordem em que chegaram. */
    protected final List<String> channelBodies = new ArrayList<>();

    @BeforeEach
    void startLogCapture() {
        logs = LogCapture.start();
        channelBodies.clear();
        // prova de que a captura enxerga o logger da aplicação (a pilha pode não logar nada)
        LoggerFactory.getLogger("br.com.felipeblaft.reports.support.RealStackBase")
                .info("captura de log ativa");
        assertThat(logs.messages()).contains("captura de log ativa");
        logs.clear();
    }

    @AfterEach
    void stopLogCapture() {
        logs.close();
    }

    // ---------------------------------------------------------------- chamadas HTTP

    protected MvcResult call(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    protected static JsonNode body(MvcResult result) throws Exception {
        String text = result.getResponse().getContentAsString();
        return JSON.readTree(text.isEmpty() ? "{}" : text);
    }

    protected static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B asJson(
            B request, String json) {
        return request.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B withCookie(
            B request, Cookie cookie) {
        return request.cookie(cookie).with(csrf().asHeader());
    }

    protected MvcResult adminGet(Cookie admin, String path) throws Exception {
        return call(get(path).cookie(admin));
    }

    protected MvcResult adminPost(Cookie admin, String path, String json) throws Exception {
        var request = withCookie(post(path), admin);
        return call(json == null ? request : asJson(request, json));
    }

    protected MvcResult adminPatch(Cookie admin, String path, String json) throws Exception {
        return call(asJson(withCookie(patch(path), admin), json));
    }

    protected MvcResult adminPut(Cookie admin, String path, String json) throws Exception {
        var request = withCookie(put(path), admin);
        return call(json == null ? request : asJson(request, json));
    }

    protected MvcResult adminDelete(Cookie admin, String path) throws Exception {
        return call(withCookie(delete(path), admin));
    }

    // ---------------------------------------------------------------- administrador

    /** Entra como administrador por e-mail e senha e devolve o resultado da chamada. */
    protected MvcResult adminSignInRequest(String email, String password) throws Exception {
        return call(
                asJson(
                        post(ADMIN_BASE + "/session").with(csrf().asHeader()),
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    protected static Cookie cookieOf(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        return cookie == null ? null : new Cookie(cookie.getName(), cookie.getValue());
    }

    /** Insere um administrador ativo e definitivo (senha conhecida) e entra com ele. */
    protected Cookie insertAndSignInAsAdministrator() throws Exception {
        AdministratorFixture fixture =
                AdministratorFixture.insertActive(administrators, hasher, clock.now());
        MvcResult result = adminSignInRequest(fixture.email(), fixture.password().value());
        mvcStatus(result, 200);
        return cookieOf(result, ADMIN_COOKIE);
    }

    /** Cria o primeiro administrador pelo caso de uso da subida, com senha provisória. */
    protected void bootstrapAdministrator(String name, String email, String provisionalPassword) {
        bootstrapAdministrator.run(
                Actor.system(ActionOrigin.SYSTEM_STARTUP),
                new BootstrapFirstAdministratorUseCase.Command(
                        name, email, new Secret(provisionalPassword)));
    }

    /** Cria um professor pela API do administrador e devolve o identificador e o código. */
    protected record CreatedTeacher(UUID id, String code, MvcResult response) {}

    protected CreatedTeacher createTeacher(Cookie admin, String name, String email)
            throws Exception {
        MvcResult result =
                adminPost(
                        admin,
                        ADMIN_BASE + "/teachers",
                        "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}");
        mvcStatus(result, 201);
        JsonNode json = body(result);
        return new CreatedTeacher(
                UUID.fromString(json.path("teacher").path("id").asString()),
                json.path("accessCode").asString(),
                result);
    }

    /** Entra como professor pelo código e devolve o cookie de sessão. */
    protected Cookie teacherSignIn(String code) throws Exception {
        MvcResult result =
                call(asJson(post("/api/v1/session").with(csrf().asHeader()),
                        "{\"code\":\"" + code + "\"}"));
        mvcStatus(result, 200);
        return cookieOf(result, "session");
    }

    protected static void mvcStatus(MvcResult result, int expected) {
        int actual = result.getResponse().getStatus();
        if (actual != expected) {
            throw new AssertionError(
                    "status " + actual + " no lugar de " + expected + " em "
                            + result.getRequest().getMethod() + " "
                            + result.getRequest().getRequestURI());
        }
    }

    // ---------------------------------------------------------------- corpos de aluno

    /** Aluno canário completo, com o CPF canário com máscara e o responsável. */
    protected static String canaryStudentJson(List<UUID> teacherIds) {
        return studentJson(
                CanaryStudent.NAME,
                CanaryStudent.BIRTH_DATE_ISO,
                CanaryCpf.MASKED,
                CanaryStudent.GUARDIAN_NAME,
                CanaryStudent.GUARDIAN_PHONE_MASKED,
                CanaryStudent.GUARDIAN_EMAIL,
                CanaryStudent.INSTRUMENT,
                teacherIds);
    }

    /** Corpo de aluno; campo nulo é omitido (a edição não muda o que está ausente). */
    protected static String studentJson(
            String name,
            String birthDate,
            String cpf,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String instrument,
            List<UUID> teacherIds) {
        List<String> fields = new ArrayList<>();
        addField(fields, "name", name);
        addField(fields, "birthDate", birthDate);
        addField(fields, "cpf", cpf);
        if (guardianName != null || guardianPhone != null || guardianEmail != null) {
            List<String> guardian = new ArrayList<>();
            addField(guardian, "name", guardianName);
            addField(guardian, "phone", guardianPhone);
            addField(guardian, "email", guardianEmail);
            fields.add("\"guardian\":{" + String.join(",", guardian) + "}");
        }
        addField(fields, "instrument", instrument);
        if (teacherIds != null) {
            fields.add(
                    "\"teacherIds\":["
                            + String.join(
                                    ",", teacherIds.stream().map(id -> "\"" + id + "\"").toList())
                            + "]");
        }
        return "{" + String.join(",", fields) + "}";
    }

    private static void addField(List<String> fields, String name, String value) {
        if (value != null) {
            fields.add("\"" + name + "\":\"" + value + "\"");
        }
    }

    // ---------------------------------------------------------------- Telegram

    /** Envia o update ao canal interno e guarda a resposta bruta (as ações) para a varredura. */
    @Override
    protected void sendUpdate(String json) throws Exception {
        if (!"internal".equals(TRANSPORT)) {
            super.sendUpdate(json);
            return;
        }
        MvcResult result =
                mvc.perform(
                                post("/internal/v1/channel/telegram/update")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andExpect(status().isOk())
                        .andReturn();
        String actions = result.getResponse().getContentAsString();
        ActionsSchemaV1.require("UpdateResponse", actions);
        channelBodies.add(actions);
        orchestratorExecutor().run(JSON.readTree(actions).get("actions"));
    }

    /** Tudo o que um passo da conversa produziu: as chamadas à API de bots e as ações do canal. */
    public record TelegramStep(String botCalls, String channelActions) {

        public String all() {
            return botCalls + "\n" + channelActions;
        }
    }

    @FunctionalInterface
    protected interface TelegramAction {
        void run() throws Exception;
    }

    /** Executa um passo e devolve o que saiu para o chat naquele passo (e só nele). */
    protected TelegramStep telegramStep(TelegramAction action) throws Exception {
        API_BOTS.resetRequests();
        channelBodies.clear();
        action.run();
        StringBuilder calls = new StringBuilder();
        for (LoggedRequest request : API_BOTS.findAll(anyRequestedFor(anyUrl()))) {
            calls.append(request.getUrl()).append(' ').append(request.getBodyAsString()).append('\n');
        }
        return new TelegramStep(calls.toString(), String.join("\n", channelBodies));
    }

    // ---------------------------------------------------------------- varredura do que ficou

    /** O texto de todos os eventos de log, com mensagem, MDC, pares e rastros. */
    protected String logText() {
        return logs.text();
    }

    /** Só os rastros de pilha (com as causas) dos eventos de log. */
    protected String traceText() {
        return PrivacyProbes.traceText(logs);
    }

    /** O registro de autoria inteiro como texto. */
    protected String authorshipText() {
        return PrivacyProbes.authorshipText(jdbc);
    }

    /** Todas as linhas de uma tabela como texto (vazio se não há linhas). */
    protected String tableText(String table) {
        return PrivacyProbes.tableText(jdbc, table);
    }

    /** Nomes das tabelas do banco, menos o histórico do Flyway. */
    protected List<String> tables() {
        return PrivacyProbes.tables(jdbc);
    }
}
