package br.com.felipeblaft.reports.realai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Suíte opcional com IA real (perfil Maven {@code ia-real}, fora da cobertura; R-16): processa as
 * gravações fictícias de {@code assets/} com FFmpeg e os provedores reais e mede o tempo até a
 * conferência (SC-001), a alteração por texto e por áudio (SC-013) e o semestral com 30 aulas
 * (SC-010), com o custo estimado de cada etapa. Escreve {@code target/ia-real/relatorio.md}.
 *
 * <p>Sem {@code TRANSCRICAO_CHAVE} e {@code LLM_CHAVE} no ambiente, a suíte é ignorada.
 */
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {"reports.providers.mode=real", "reports.test.simulated-normalization=false"})
class RealAiSuiteIT extends IntegrationBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration META_REVIEW = Duration.ofMinutes(2);
    private static final String RESPONSE =
            "Estudou três vezes na semana e vai continuar com a mesma peça na próxima aula.";

    @Autowired private MockMvc mvc;
    @Autowired private ScenarioIT scenario;

    private Cookie session;

    private record Measure(
            String step,
            String file,
            double megabytes,
            long milliseconds,
            BigDecimal cost,
            String result) {}

    private final List<Measure> measures = new ArrayList<>();

    /** O que mudou no relatório quando a IA incorporou a resposta às perguntas (FR-025). */
    private record Incorporation(
            String file, List<String> changedSections, boolean answerOnlyInObservations) {

        boolean adequate() {
            return !changedSections.isEmpty() && !answerOnlyInObservations;
        }
    }

    private static final List<String> SECTIONS_WITHOUT_OBSERVATIONS =
            List.of("workedTopics", "progress", "difficulties", "proposedActivities");

    private final List<Incorporation> incorporations = new ArrayList<>();

    @Test
    void clientRecordingsWithRealAi() throws Exception {
        assumeTrue(
                hasKey("TRANSCRIPTION_API_KEY") && hasKey("LLM_API_KEY"),
                "sem chaves dos provedores de IA");
        Path assets = Path.of(System.getenv().getOrDefault("ASSETS_DIR", "../../assets"));
        List<Path> recordings;
        try (Stream<Path> files = Files.list(assets)) {
            recordings =
                    files.filter(a -> a.getFileName().toString().matches("(?i).+\\.(ogg|mp4)$"))
                            .sorted()
                            .toList();
        }
        assumeTrue(!recordings.isEmpty(), "sem gravações em " + assets.toAbsolutePath());

        Teacher clara = scenario.clara();
        session = signIn();
        UUID firstReport = null;
        Path firstRecording = null;
        for (Path recording : recordings) {
            Student student = scenario.student(clara, studentName(recording), "Piano");
            UUID report = register(student, recording);
            if (firstReport == null && report != null) {
                firstReport = report;
                firstRecording = recording;
            }
        }
        assertThat(firstReport).as("ao menos uma gravação chegou à conferência").isNotNull();

        changeByText(firstReport);
        changeByAudio(firstReport, firstRecording);
        semesterWithThirtyLessons(clara);

        writeReport();
        assertThat(measures)
                .filteredOn(m -> m.step().equals("Registro até a conferência"))
                .allSatisfy(m -> assertThat(m.result()).isEqualTo("AWAITING_REVIEW"));
        // FR-025: a resposta entrou nas seções adequadas, não só anexada às observações
        assertThat(incorporations).as("ao menos uma gravação teve perguntas").isNotEmpty();
        assertThat(incorporations)
                .filteredOn(i -> !i.adequate())
                .as("incorporações que só anexaram a resposta às observações")
                .isEmpty();
    }

    // ------------------------------------------------------------------ etapas

    private UUID register(Student student, Path recording) throws Exception {
        long start = System.nanoTime();
        byte[] content = Files.readAllBytes(recording);
        MvcResult sending =
                mvc.perform(
                                multipart("/api/v1/students/{id}/lesson-reports", student.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "lesson" + extension(recording),
                                                        type(recording),
                                                        content))
                                        .cookie(session)
                                        .with(csrf().asHeader()))
                        .andReturn();
        if (sending.getResponse().getStatus() != 202) {
            measures.add(
                    new Measure(
                            "Registro até a conferência",
                            name(recording),
                            mb(content.length),
                            millis(start),
                            BigDecimal.ZERO,
                            "RECUSADO " + sending.getResponse().getStatus()));
            return null;
        }
        scenario.processQueue();
        UUID report = studentReport(student);
        if (report != null && "AWAITING_ANSWERS".equals(status(report))) {
            JsonNode before = content(report);
            postJson("/api/v1/lesson-reports/" + report + "/answers", Map.of("text", RESPONSE));
            scenario.processQueue();
            incorporations.add(incorporation(name(recording), before, content(report)));
        }
        String result = report == null ? lastJobResult() : status(report);
        measures.add(
                new Measure(
                        "Registro até a conferência",
                        name(recording),
                        mb(content.length),
                        millis(start),
                        cost(report),
                        result));
        return "AWAITING_REVIEW".equals(result) ? report : null;
    }

    private JsonNode content(UUID report) {
        return JSON.readTree(
                jdbc.queryForObject(
                        "select content::text from lesson_report where id = ?",
                        String.class,
                        report));
    }

    private static Incorporation incorporation(String file, JsonNode before, JsonNode after) {
        List<String> changed =
                SECTIONS_WITHOUT_OBSERVATIONS.stream()
                        .filter(section -> !before.path(section).equals(after.path(section)))
                        .toList();
        String observations = after.path("observations").asString("");
        boolean onlyAttached =
                changed.isEmpty() && !observations.equals(before.path("observations").asString(""));
        return new Incorporation(file, changed, onlyAttached);
    }

    private void changeByText(UUID report) throws Exception {
        BigDecimal before = cost(report);
        long start = System.nanoTime();
        postJson(
                "/api/v1/lesson-reports/" + report + "/changes",
                Map.of(
                        "text",
                        "Acrescente nas observações que o aluno chegou pontualmente e com o material.",
                        "baseVersion",
                        version(report)));
        scenario.processQueue();
        measures.add(
                new Measure(
                        "Alteração por texto",
                        "—",
                        0,
                        millis(start),
                        cost(report).subtract(before),
                        "versão " + version(report)));
    }

    private void changeByAudio(UUID report, Path recording) throws Exception {
        Path excerpt = Files.createTempFile("excerpt-", ".ogg");
        try {
            Process ffmpeg =
                    new ProcessBuilder(
                                    "ffmpeg",
                                    "-y",
                                    "-loglevel",
                                    "error",
                                    "-i",
                                    recording.toString(),
                                    "-t",
                                    "15",
                                    "-vn",
                                    "-c:a",
                                    "libopus",
                                    excerpt.toString())
                            .redirectErrorStream(true)
                            .start();
            assertThat(ffmpeg.waitFor(60, TimeUnit.SECONDS)).isTrue();
            byte[] content = Files.readAllBytes(excerpt);
            BigDecimal before = cost(report);
            int versionBefore = version(report);
            long start = System.nanoTime();
            mvc.perform(
                    multipart("/api/v1/lesson-reports/{id}/changes", report)
                            .file(
                                    new MockMultipartFile(
                                            "audio", "change.ogg", "audio/ogg", content))
                            .param("baseVersion", Integer.toString(versionBefore))
                            .cookie(session)
                            .with(csrf().asHeader()));
            scenario.processQueue();
            measures.add(
                    new Measure(
                            "Alteração por áudio (15 s)",
                            name(recording),
                            mb(content.length),
                            millis(start),
                            cost(report).subtract(before),
                            version(report) > versionBefore
                                    ? "versão " + version(report)
                                    : lastJobResult()));
        } finally {
            Files.deleteIfExists(excerpt);
        }
    }

    private void semesterWithThirtyLessons(Teacher clara) throws Exception {
        Student student = scenario.student(clara, "Aluno Fictício do Semestral", "Piano");
        List<String> themes =
                List.of(
                        "Escala de Dó maior em duas oitavas",
                        "Estudo nº 5 de Czerny, op. 599",
                        "Leitura à primeira vista em clave de fá",
                        "Minueto em Sol maior de Petzold",
                        "Arpejos de Lá menor",
                        "Dinâmica e fraseado na sonatina de Clementi");
        LocalDate data = LocalDate.of(2026, 1, 12);
        for (int i = 0; i < 30; i++) {
            scenario.approvedLesson(
                    clara, student, data.plusDays(5L * i), themes.get(i % themes.size()));
        }
        long start = System.nanoTime();
        postJson(
                "/api/v1/students/" + student.id() + "/semester-reports",
                Map.of("periodKey", "2026-1"));
        scenario.processQueue();
        BigDecimal cost =
                jdbc.queryForObject(
                        "select coalesce(sum(estimated_cost_usd), 0) from ai_call where report_type = 'SEMESTER'",
                        BigDecimal.class);
        String status =
                jdbc.queryForList("select status from semester_report", String.class).stream()
                        .findFirst()
                        .orElse(lastJobResult());
        measures.add(new Measure("Semestral com 30 aulas", "—", 0, millis(start), cost, status));
    }

    // ------------------------------------------------------------------ relatório

    private void writeReport() throws IOException {
        StringBuilder md = new StringBuilder("# Suíte com IA real\n\n");
        md.append("Provedores: transcrição `")
                .append(System.getenv().getOrDefault("TRANSCRIPTION_MODEL", "padrão"))
                .append("`, LLM `")
                .append(System.getenv().getOrDefault("LLM_MODEL", "padrão"))
                .append("`.\n\n");
        md.append("| Etapa | Arquivo | MB | Tempo (s) | Custo (USD) | Resultado |\n");
        md.append("|---|---|---:|---:|---:|---|\n");
        for (Measure m : measures) {
            md.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %s | %.1f | %.1f | %.4f | %s |%n",
                            m.step(),
                            m.file(),
                            m.megabytes(),
                            m.milliseconds() / 1000.0,
                            m.cost(),
                            m.result()));
        }
        List<Measure> registrations =
                measures.stream()
                        .filter(m -> m.step().equals("Registro até a conferência"))
                        .toList();
        long withinGoal =
                registrations.stream()
                        .filter(m -> m.milliseconds() <= META_REVIEW.toMillis())
                        .count();
        md.append(
                String.format(
                        Locale.ROOT,
                        "%nSC-001: %d de %d gravações chegaram à conferência em até 2 min (meta: 90%%).%n",
                        withinGoal,
                        registrations.size()));
        md.append(
                String.format(
                        Locale.ROOT,
                        "Custo total estimado: US$ %.4f.%n",
                        measures.stream()
                                .map(Measure::cost)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        md.append("\n## Incorporação das respostas às perguntas (FR-025)\n\n");
        md.append("| Arquivo | Seções alteradas além de `observacoes` | Resultado |\n");
        md.append("|---|---|---|\n");
        for (Incorporation i : incorporations) {
            md.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %s | %s |%n",
                            i.file(),
                            i.changedSections().isEmpty()
                                    ? "—"
                                    : String.join(", ", i.changedSections()),
                            i.adequate()
                                    ? "incorporada nas seções"
                                    : "apenas anexada às observações"));
        }
        Path destination = Path.of("target", "real-ai", "report.md");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, md);
    }

    // ------------------------------------------------------------------ apoio

    private Cookie signIn() throws Exception {
        MvcResult input =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("session");
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private void postJson(String path, Object body) throws Exception {
        mvc.perform(
                post(path)
                        .cookie(session)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(body)));
    }

    private int version(UUID report) throws Exception {
        JsonNode dto =
                JSON.readTree(
                        mvc.perform(get("/api/v1/lesson-reports/{id}", report).cookie(session))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return dto.path("currentVersion").asInt();
    }

    private UUID studentReport(Student student) {
        return jdbc
                .queryForList(
                        "select id from lesson_report where student_id = ?",
                        UUID.class,
                        student.id())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String status(UUID report) {
        return jdbc.queryForObject(
                "select status from lesson_report where id = ?", String.class, report);
    }

    private String lastJobResult() {
        return jdbc
                .queryForList(
                        "select coalesce(result_code, status) from processing_job order by created_at desc limit 1",
                        String.class)
                .stream()
                .findFirst()
                .orElse("SEM JOB");
    }

    private BigDecimal cost(UUID report) {
        if (report == null) {
            return BigDecimal.ZERO;
        }
        return jdbc.queryForObject(
                "select coalesce(sum(estimated_cost_usd), 0) from ai_call where report_id = ?",
                BigDecimal.class,
                report);
    }

    private static boolean hasKey(String variable) {
        String value = System.getenv(variable);
        return value != null && !value.isBlank();
    }

    /**
     * "Aula Matteo Mafra_21_09_2026.ogg" → "Matteo Mafra" (nomes fictícios das gravações de teste).
     */
    static String studentName(Path recording) {
        String name = recording.getFileName().toString().replaceFirst("(?i)^Lesson\\s+", "");
        int end = name.indexOf('_');
        return (end > 0 ? name.substring(0, end) : name.replaceFirst("\\.[^.]+$", "")).strip();
    }

    private static String name(Path recording) {
        return recording.getFileName().toString();
    }

    private static String extension(Path recording) {
        String name = name(recording);
        return name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private static String type(Path recording) {
        return extension(recording).equals(".mp4") ? "video/mp4" : "audio/ogg";
    }

    private static double mb(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static long millis(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }
}
