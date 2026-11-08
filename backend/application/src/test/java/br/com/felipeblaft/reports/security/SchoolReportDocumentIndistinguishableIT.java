package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Indistinguibilidade do PDF da visão da escola (feature 006, FR-018, SC-003): relatório
 * inexistente, relatório em cada estado não aprovado e rota inexistente devolvem o mesmo status, o
 * mesmo corpo e os mesmos cabeçalhos relevantes, e o tempo não separa o inexistente do não aprovado
 * (razão das medianas em [0,5; 2,0]; o teste imprime a razão). Medição em processo, com aquecimento
 * e semente fixa, no molde de {@code IndistinguishableStudentResponseIT}.
 */
class SchoolReportDocumentIndistinguishableIT extends SchoolHistoryBase {

    private static final Set<String> VOLATILE_HEADERS = Set.of("date", "set-cookie");
    private static final List<String> LESSON_STATES =
            List.of(
                    "PROCESSING",
                    "PROCESSING_FAILED",
                    "AWAITING_ANSWERS",
                    "AWAITING_REVIEW",
                    "CANCELLED");
    private static final int WARM_UP = 20;
    private static final int MEASUREMENTS = 60;
    private static final long SEED = 20_261_103L;

    private Cookie administrator;
    private UUID lesson;
    private UUID semester;

    @BeforeEach
    void prepare() throws Exception {
        Teacher clara = scenario.clara();
        Student student = scenario.student(clara, "Matteo Mafra", "Piano");
        lesson = scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Sopro").id();
        administrator = signInNewAdministrator();
        Cookie claraCookie = signInTeacher(ScenarioIT.CODE_CLARA);
        semester = approvedSemester(claraCookie, student.id(), "2026-1");
    }

    private MockHttpServletResponse get(String path) throws Exception {
        return call(HttpMethod.GET, path, administrator);
    }

    private static String fingerprint(MockHttpServletResponse response) throws Exception {
        Map<String, List<String>> headers = new TreeMap<>();
        for (String name : response.getHeaderNames()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!VOLATILE_HEADERS.contains(lower)) {
                headers.put(lower, response.getHeaders(name));
            }
        }
        return response.getStatus() + "\n" + headers + "\n" + response.getContentAsString();
    }

    private String missingFingerprint(boolean lessonRoute, boolean download) throws Exception {
        UUID absent = UUID.fromString("00000000-0000-4000-8000-000000000999");
        return fingerprint(get(path(lessonRoute, download, absent)));
    }

    private String path(boolean lessonRoute, boolean download, UUID id) {
        if (lessonRoute) {
            return download ? lessonDownload(id) : lessonPdf(id);
        }
        return download ? semesterDownload(id) : semesterPdf(id);
    }

    @Test
    void everyNotApprovedStateLooksLikeAMissingReportAndLikeAnUnknownRoute() throws Exception {
        String unknownRoute = fingerprint(get(ADMIN + "/this-route-does-not-exist"));
        assertThat(unknownRoute).startsWith("404");

        List<String> differences = new ArrayList<>();
        for (String state : LESSON_STATES) {
            revertLessonTo(lesson, state);
            for (boolean download : List.of(false, true)) {
                String missing = missingFingerprint(true, download);
                String actual = fingerprint(get(path(true, download, lesson)));
                if (!actual.equals(missing)) {
                    differences.add("aula " + state + " download=" + download);
                }
                if (!missing.equals(unknownRoute)) {
                    differences.add("inexistente (aula) difere da rota inexistente");
                }
            }
        }
        for (String state : List.of("AWAITING_REVIEW", "CANCELLED", "PROCESSING_FAILED")) {
            revertSemesterTo(semester, state);
            for (boolean download : List.of(false, true)) {
                String missing = missingFingerprint(false, download);
                String actual = fingerprint(get(path(false, download, semester)));
                if (!actual.equals(missing)) {
                    differences.add("semestral " + state + " download=" + download);
                }
            }
        }
        assertThat(differences).as("respostas distinguíveis").isEmpty();
    }

    @Test
    void theNotApprovedResponseDoesNotRevealPdfOrReportData() throws Exception {
        revertLessonTo(lesson, "AWAITING_REVIEW");

        MockHttpServletResponse response = get(lessonPdf(lesson));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(
                        new String(
                                response.getContentAsByteArray(),
                                java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotStartWith("%PDF");
        assertThat(response.getContentAsString())
                .doesNotContain("Matteo")
                .doesNotContain("Sopro")
                .doesNotContain(lesson.toString());
    }

    @Test
    void timingDoesNotSeparateTheMissingReportFromTheNotApprovedOne() throws Exception {
        revertLessonTo(lesson, "AWAITING_REVIEW");
        String notApproved = lessonPdf(lesson);
        String missing = lessonPdf(UUID.fromString("00000000-0000-4000-8000-000000000999"));

        for (int i = 0; i < WARM_UP; i++) {
            get(notApproved);
            get(missing);
        }
        List<Boolean> order = new ArrayList<>();
        for (int i = 0; i < MEASUREMENTS; i++) {
            order.add(true);
            order.add(false);
        }
        Collections.shuffle(order, new Random(SEED));
        List<Long> notApprovedTimes = new ArrayList<>();
        List<Long> missingTimes = new ArrayList<>();
        for (boolean useNotApproved : order) {
            long start = System.nanoTime();
            get(useNotApproved ? notApproved : missing);
            long elapsed = System.nanoTime() - start;
            (useNotApproved ? notApprovedTimes : missingTimes).add(elapsed);
        }

        double ratio = (double) median(notApprovedTimes) / median(missingTimes);
        System.out.printf(
                Locale.ROOT, "razão das medianas (não aprovado ÷ inexistente): %.2f%n", ratio);
        assertThat(ratio).isBetween(0.5, 2.0);
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
