package br.com.felipeblaft.reports.privacy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.outbound.ai.LlmOpenAiCompatibleAdapter;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.RealStackBase;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.support.ScenarioIT;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * FR-017, FR-044 e Princípio IV: com o aluno canário (CPF, nascimento e responsável) cadastrado, a
 * geração do relatório de aula (estruturação, resposta, alteração) e a do semestral
 * (consolidação, alteração) não enviam à IA nada do aluno: a entrada capturada na porta, a
 * mensagem de sistema e a do usuário montadas pelo adaptador real (reproduzidas num servidor
 * simulado) e o texto do relatório (JSON e PDF) não têm CPF nem dado do responsável. Pilha real,
 * com a porta de IA espionada.
 */
class StudentDataNotSentToAiIT extends RealStackBase {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String PATH = "/v1/chat/completions";
    private static final String ANSWER = "O aluno treinou o trecho final devagar durante a semana";
    private static final String CHANGE = "Incluir a observação sobre a postura ao tocar";

    private static final WireMockServer PROVIDER = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    static void startProvider() {
        PROVIDER.start();
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop();
    }

    /** Uma chamada à porta de IA: a tarefa, a entrada, o tipo pedido e a resposta. */
    private record Captured(TaskLlm task, TaskInput input, Class<?> type, ResponseLlm<?> response) {}

    @MockitoSpyBean LlmPort llm;
    @Autowired StudentRepository students;

    private final List<Captured> captured = new CopyOnWriteArrayList<>();

    @BeforeEach
    void captureTheAiPort() {
        captured.clear();
        Mockito.doAnswer(
                        call -> {
                            Object response = call.callRealMethod();
                            captured.add(
                                    new Captured(
                                            call.getArgument(0),
                                            call.getArgument(1),
                                            call.getArgument(2),
                                            (ResponseLlm<?>) response));
                            return response;
                        })
                .when(llm)
                .generateJson(any(), any(), any());
    }

    /** Nada do aluno pode ir à IA, nem o nome, nem o instrumento: o contrato só autoriza o conteúdo. */
    private static Map<String, String> forbiddenInAiInput() {
        Map<String, String> values = new LinkedHashMap<>(CanaryStudent.sweepValuesWithCpf());
        values.put("instrumento do aluno canário", CanaryStudent.INSTRUMENT);
        return values;
    }

    /** No relatório pode haver o nome do aluno; nunca o CPF nem dado do responsável. */
    private static Map<String, String> forbiddenInReport() {
        Map<String, String> values = new LinkedHashMap<>(CanaryCpf.sweepValues());
        values.put("nome do responsável canário", CanaryStudent.GUARDIAN_NAME);
        values.put("telefone do responsável canário", CanaryStudent.GUARDIAN_PHONE_DIGITS);
        values.put("e-mail do responsável canário", CanaryStudent.GUARDIAN_EMAIL);
        return values;
    }

    @Test
    void lessonAndSemesterGenerationSendNothingOfTheStudentToTheAi() throws Exception {
        Teacher clara = scenario.clara();
        Cookie session = teacherSignIn(ScenarioIT.CODE_CLARA);
        MvcResult registered =
                call(
                        asJson(
                                withCookie(
                                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .post("/api/v1/my-students"),
                                        session),
                                canaryStudentJson(null)));
        mvcStatus(registered, 201);
        UUID studentId = UUID.fromString(body(registered).path("id").asString());
        Student student = students.searchById(studentId).orElseThrow();
        // o aluno guarda mesmo o CPF e o responsável que a varredura procura
        assertThat(jdbc.queryForObject("select count(*) from student where guardian_name is not null", Integer.class))
                .isEqualTo(1);
        captured.clear();

        LeakAudit report = LeakAudit.searching(forbiddenInReport());

        // relatório de aula: áudio, resposta, alteração, aprovação
        MvcResult upload =
                call(
                        multipart("/api/v1/students/{id}/lesson-reports", studentId)
                                .file(
                                        new MockMultipartFile(
                                                "audio",
                                                "lesson.webm",
                                                "audio/webm",
                                                AudioNormalizationTest.AUDIO_WITH_SPEECH))
                                .cookie(session)
                                .with(csrf().asHeader()));
        assertThat(upload.getResponse().getStatus()).isEqualTo(202);
        scenario.processQueue();
        UUID lessonReport = jdbc.queryForObject("select id from lesson_report", UUID.class);
        report.exchange(
                "relatório de aula (estruturado)",
                call(get("/api/v1/lesson-reports/{id}", lessonReport).cookie(session)));
        assertThat(jdbc.queryForObject("select status from lesson_report", String.class))
                .as("a estruturação simulada faz perguntas")
                .isEqualTo("AWAITING_ANSWERS");
        mvcStatus(
                call(
                        asJson(
                                withCookie(
                                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .post("/api/v1/lesson-reports/{id}/answers", lessonReport),
                                        session),
                                "{\"text\":\"" + ANSWER + "\"}")),
                202);
        scenario.processQueue();
        int version =
                jdbc.queryForObject("select current_version from lesson_report where id = ?", Integer.class, lessonReport);
        mvcStatus(
                call(
                        asJson(
                                withCookie(
                                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .post("/api/v1/lesson-reports/{id}/changes", lessonReport),
                                        session),
                                "{\"text\":\"" + CHANGE + "\",\"baseVersion\":" + version + "}")),
                202);
        scenario.processQueue();
        report.exchange(
                "relatório de aula (alterado)",
                call(get("/api/v1/lesson-reports/{id}", lessonReport).cookie(session)));
        report.text("PDF do relatório de aula", Place.BODY, pdfText(session, "/api/v1/lesson-reports/" + lessonReport + "/versions/1/pdf"));

        // relatório semestral: aulas aprovadas, consolidação, alteração
        scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Escala de Dó maior");
        scenario.approvedLesson(clara, student, LocalDate.of(2026, 5, 20), "Estudo número cinco");
        mvcStatus(
                call(
                        asJson(
                                withCookie(
                                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .post("/api/v1/students/{id}/semester-reports", studentId),
                                        session),
                                "{\"periodKey\":\"2026-1\",\"confirmDespiteOpenReports\":true}")),
                202);
        scenario.processQueue();
        UUID semester = jdbc.queryForObject("select id from semester_report", UUID.class);
        report.exchange(
                "relatório semestral",
                call(get("/api/v1/semester-reports/{id}", semester).cookie(session)));
        mvcStatus(
                call(
                        asJson(
                                withCookie(
                                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .post("/api/v1/semester-reports/{id}/changes", semester),
                                        session),
                                "{\"text\":\"Parecer: " + CHANGE + "\",\"baseVersion\":1}")),
                202);
        scenario.processQueue();
        report.exchange(
                "relatório semestral (alterado)",
                call(get("/api/v1/semester-reports/{id}", semester).cookie(session)));
        report.text("PDF do relatório semestral", Place.BODY, pdfText(session, "/api/v1/semester-reports/" + semester + "/versions/1/pdf"));

        // o que a porta de IA recebeu
        List<Captured> calls = List.copyOf(captured);
        List<TaskLlm> tasks = calls.stream().map(Captured::task).toList();
        assertThat(tasks)
                .as("as tarefas de IA percorridas")
                .contains(
                        TaskLlm.LESSON_STRUCTURING,
                        TaskLlm.ANSWER_INCORPORATION,
                        TaskLlm.LESSON_REVISION,
                        TaskLlm.SEMESTER_CONSOLIDATION,
                        TaskLlm.SEMESTER_REVISION);

        LeakAudit ai = LeakAudit.searching(forbiddenInAiInput());
        for (Captured call : calls) {
            TaskLlm task = call.task();
            ai.text("entrada da tarefa " + task, Place.ACTION_PAYLOAD, JSON_MAPPER.writeValueAsString(call.input()));
            ai.text("texto da entrada (toString) da tarefa " + task, Place.ACTION_PAYLOAD, String.valueOf(call.input()));
            ai.text("saída da tarefa " + task, Place.ACTION_PAYLOAD, JSON_MAPPER.writeValueAsString(call.response().output()));
        }

        // o prompt: as mensagens que o adaptador real monta com as mesmas entradas
        int requests = replayThroughTheRealAdapter(calls, ai);

        ai.text("log", Place.LOG, logText());
        report.text("log", Place.LOG, logText());
        for (String table : tables()) {
            if (!table.equals("student")) {
                report.text("tabela " + table, Place.AUTHORSHIP, tableText(table));
            }
        }

        assertThat(requests).as("pedidos ao provedor simulado").isGreaterThanOrEqualTo(calls.size());
        assertThat(ai.sweeps()).isGreaterThan(calls.size() * 3);
        ai.assertClean();
        report.assertClean();
    }

    /** Reproduz cada chamada no adaptador real e varre o que ele enviaria ao provedor. */
    private int replayThroughTheRealAdapter(List<Captured> calls, LeakAudit ai) throws Exception {
        PROVIDER.resetAll();
        LlmOpenAiCompatibleAdapter adapter =
                new LlmOpenAiCompatibleAdapter(
                        new ApplicationProperties.Llm(
                                PROVIDER.baseUrl() + "/v1",
                                "test-model",
                                "test-key",
                                new BigDecimal("2"),
                                new BigDecimal("8")));
        for (Captured call : calls) {
            ResponseLlm<?> response = call.response();
            PROVIDER.stubFor(
                    post(urlEqualTo(PATH))
                            .willReturn(
                                    aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(completion(JSON_MAPPER.writeValueAsString(response.output())))));
            try {
                adapter.generateJson(call.task(), call.input(), call.type());
            } catch (RuntimeException invalidOutputOrUnavailable) {
                // só interessa o que foi enviado; a saída reproduzida pode não passar no esquema
            }
        }
        List<LoggedRequest> sent = PROVIDER.findAll(postRequestedFor(urlEqualTo(PATH)));
        for (LoggedRequest request : sent) {
            ai.text("pedido ao provedor", Place.ACTION_PAYLOAD, request.getBodyAsString());
        }
        return sent.size();
    }

    private static String completion(String content) {
        return JSON_MAPPER.writeValueAsString(
                Map.of(
                        "choices",
                        List.of(Map.of("message", Map.of("content", content))),
                        "usage",
                        Map.of("prompt_tokens", 10, "completion_tokens", 10)));
    }

    private String pdfText(Cookie session, String path) throws Exception {
        MvcResult result = call(get(path).cookie(session).accept(MediaType.APPLICATION_PDF));
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return textOfPdf(result.getResponse().getContentAsByteArray());
    }

    private static String textOfPdf(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(pdf);
        }
    }
}
