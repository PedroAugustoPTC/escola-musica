package br.com.felipeblaft.reports.security;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Base dos testes transversais de acesso e isolamento da feature 005: a pilha real (cadeias de
 * segurança, casos de uso, Postgres e o fluxo do Telegram, sem dublê de caso de uso), com ajudantes
 * para entrar como administrador ou professor, chamar uma rota qualquer com ou sem credencial e ler
 * o que o bot mandou.
 */
abstract class SecurityBase extends TelegramConversationBase {

    protected static final String ADMIN_COOKIE = "admin_session";
    protected static final String API = "/api/v1";

    @Autowired protected AdministratorRepository administrators;
    @Autowired protected PasswordHashPort hasher;

    // ------------------------------------------------------------------ Web

    /** Insere o administrador de teste (uma vez por teste) e entra; devolve o cookie da sessão. */
    protected Cookie signInAdministrator() throws Exception {
        AdministratorFixture fixture;
        try {
            fixture = AdministratorFixture.insertActive(administrators, hasher, clock.now());
        } catch (IllegalStateException alreadyThere) {
            fixture =
                    new AdministratorFixture(
                            administrators
                                    .searchByEmail(AdministratorFixture.DEFAULT_EMAIL)
                                    .orElseThrow(),
                            AdministratorFixture.DEFAULT_EMAIL,
                            new br.com.felipeblaft.reports.domain.common.Secret(
                                    AdministratorFixture.DEFAULT_PASSWORD));
        }
        MvcResult result =
                call(
                        HttpMethod.POST,
                        API + "/admin/session",
                        "{\"email\":\""
                                + fixture.email()
                                + "\",\"password\":\""
                                + fixture.password().value()
                                + "\"}");
        assertThat(result.getResponse().getStatus()).as("entrada do administrador").isEqualTo(200);
        return copyOf(result.getResponse().getCookie(ADMIN_COOKIE));
    }

    /** Entra como professor pelo código e devolve o cookie da sessão. */
    protected Cookie signInTeacher(String code) throws Exception {
        MvcResult result = call(HttpMethod.POST, API + "/session", "{\"code\":\"" + code + "\"}");
        assertThat(result.getResponse().getStatus()).as("entrada do professor").isEqualTo(200);
        return copyOf(result.getResponse().getCookie("session"));
    }

    private static Cookie copyOf(Cookie cookie) {
        assertThat(cookie).as("cookie de sessão").isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    /** Chama a rota (caminho completo, com {@code /api/v1}) com os cookies e o token de CSRF. */
    protected MvcResult call(HttpMethod method, String path, String body, Cookie... cookies)
            throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        for (Cookie cookie : cookies) {
            builder.cookie(cookie);
        }
        return mvc.perform(builder.with(csrf().asHeader())).andReturn();
    }

    protected static String errorCode(MvcResult result) {
        String body = bodyOf(result);
        if (body.isBlank() || !body.startsWith("{")) {
            return "";
        }
        return JSON.readTree(body).path("code").asString("");
    }

    protected static String bodyOf(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    protected static JsonNode jsonOf(MvcResult result) {
        return JSON.readTree(bodyOf(result));
    }

    protected int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    protected int denials() {
        return jdbc.queryForObject(
                "select count(*) from authorship_record where operation = 'AUTHORIZATION_DENIED'",
                Integer.class);
    }

    // ------------------------------------------------------------------ Telegram

    private static final Pattern BOT_METHOD =
            Pattern.compile(
                    ".*/(sendMessage|editMessageText|deleteMessage|answerCallbackQuery|sendDocument)$");

    /** O que o bot fez em resposta a um update: textos, botões e métodos chamados. */
    protected record Reply(
            List<String> texts, List<String> buttonData, List<String> labels, List<String> methods) {

        /** Todos os textos e rótulos de botão juntos. */
        public String plain() {
            return String.join("\n", texts)
                    + (labels.isEmpty() ? "" : "\n" + String.join("\n", labels));
        }

        /** Texto, botões e métodos: dois updates com a mesma resposta têm a mesma impressão. */
        public String fingerprint() {
            return String.join("\n", methods)
                    + "\n"
                    + plain()
                    + "\n"
                    + String.join(",", buttonData);
        }

        public boolean isEmpty() {
            return methods.isEmpty();
        }
    }

    /** Envia o texto como o usuário do Telegram e devolve a resposta do bot. */
    protected Reply say(long user, String text) throws Exception {
        API_BOTS.resetRequests();
        sendText(user, text);
        return reply();
    }

    /** Toca o botão (envia o {@code callback_data}) e devolve a resposta do bot. */
    protected Reply tap(long user, String data) throws Exception {
        API_BOTS.resetRequests();
        sendCallback(user, data);
        return reply();
    }

    /** As chamadas à API de bots desde o último {@code resetRequests}, na ordem. */
    protected Reply reply() {
        List<String> texts = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        for (LoggedRequest logged : API_BOTS.findAll(postRequestedFor(anyUrl()))) {
            var matcher = BOT_METHOD.matcher(logged.getUrl());
            if (!matcher.matches()) {
                continue;
            }
            String method = matcher.group(1);
            methods.add(method);
            String contentType = logged.getHeader("Content-Type");
            if (contentType == null || !contentType.startsWith("application/json")) {
                continue;
            }
            JsonNode body = JSON.readTree(logged.getBodyAsString());
            if (body.has("text")) {
                texts.add(body.get("text").asString());
            }
            for (JsonNode line : body.path("reply_markup").path("inline_keyboard")) {
                for (JsonNode button : line) {
                    buttons.add(button.path("callback_data").asString(""));
                    labels.add(button.path("text").asString(""));
                }
            }
        }
        return new Reply(texts, buttons, labels, methods);
    }

    // ------------------------------------------------------------------ cenários

    @Autowired protected StudentRepository studentRepository;

    /** Relatórios de um aluno, prontos para as tentativas de acesso: ids de aula, semestral e job. */
    protected record Reports(UUID approvedLesson, UUID openLesson, UUID semester, UUID job) {}

    /**
     * Cria os relatórios do aluno do professor: uma aula aprovada, uma aula em andamento (áudio
     * processado), um semestral gerado e o trabalho que o gerou.
     */
    protected Reports createReports(Teacher teacher, Student student, Cookie session)
            throws Exception {
        UUID approved =
                scenario.approvedLesson(
                                teacher,
                                student,
                                LocalDate.of(2026, 3, 10),
                                "Sopro com apoio diafragmático")
                        .id();
        MvcResult generation =
                call(
                        HttpMethod.POST,
                        API + "/students/" + student.id() + "/semester-reports",
                        "{\"periodKey\":\"2026-1\"}",
                        session);
        UUID job = UUID.fromString(jsonOf(generation).path("jobId").asString());
        mvc.perform(
                multipart(API + "/students/" + student.id() + "/lesson-reports")
                        .file(audio())
                        .cookie(session)
                        .with(csrf().asHeader()));
        scenario.processQueue();
        UUID semester =
                jdbc.queryForObject(
                        "select id from semester_report where student_id = ?",
                        UUID.class,
                        student.id());
        UUID open =
                jdbc.queryForObject(
                        "select id from lesson_report where student_id = ? and status <> 'APPROVED'",
                        UUID.class,
                        student.id());
        return new Reports(approved, open, semester, job);
    }

    protected static MockMultipartFile audio() {
        return new MockMultipartFile(
                "audio", "lesson.webm", "audio/webm", AudioNormalizationTest.AUDIO_WITH_SPEECH);
    }

    /** Resumo (md5) do conteúdo das tabelas de negócio: prova de que nada foi alterado. */
    protected String businessDataDigest() {
        StringBuilder digest = new StringBuilder();
        for (String table :
                List.of(
                        "student",
                        "teacher",
                        "teacher_student_assignment",
                        "lesson",
                        "lesson_report",
                        "report_version",
                        "semester_report",
                        "processing_job",
                        "report_interaction")) {
            digest.append(table)
                    .append('=')
                    .append(
                            jdbc.queryForObject(
                                    "select coalesce(md5(string_agg(t::text, '|' order by t::text)), '-') from "
                                            + table
                                            + " t",
                                    String.class))
                    .append(';');
        }
        return digest.toString();
    }

    /** Duas execuções ao mesmo tempo, largando juntas; devolve os resultados na ordem. */
    protected static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return task.call();
                                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(90, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    /** CPF válido (dígitos verificadores corretos) a partir de uma semente de 9 dígitos. */
    protected static String validCpf(int seed) {
        String base = String.format("%09d", 100_000_000 + seed);
        int first = checkDigit(base, 10);
        int second = checkDigit(base + first, 11);
        return base + first + second;
    }

    private static int checkDigit(String digits, int weight) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * (weight - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
