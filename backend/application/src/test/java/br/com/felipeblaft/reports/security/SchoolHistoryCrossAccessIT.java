package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Acesso cruzado da visão da escola nos dois sentidos de credencial (feature 006, US2, SC-004):
 * o professor (inclusive o autor) não alcança nenhuma rota {@code /admin/school-history/**}; o
 * administrador, sozinho, não alcança nenhuma rota de relatório do professor; sem sessão, com
 * sessão expirada e depois de "Sair", tudo é 401; não existe rota por versão nem de escrita, e
 * nenhum relatório muda. Em todos os casos negados a resposta é igual à de rota inexistente. Pilha
 * real (Postgres, cadeias de segurança e casos de uso verdadeiros).
 *
 * <p>Fora do alcance desta tarefa: a credencial cruzada ainda não grava {@code
 * AUTHORIZATION_DENIED} (pendência aceita da feature 005); a verificação do evento E7 é da US4.
 */
class SchoolHistoryCrossAccessIT extends SchoolHistoryBase {

    private static final Set<String> VOLATILE_HEADERS = Set.of("date", "set-cookie");
    private static final String UNKNOWN_ADMIN_ROUTE = ADMIN + "/this-route-does-not-exist";
    private static final String UNKNOWN_TEACHER_ROUTE = "/api/v1/this-route-does-not-exist";

    private Cookie administrator;
    private Cookie clara;
    private UUID student;
    private UUID lesson;
    private UUID semester;

    @BeforeEach
    void prepare() throws Exception {
        Teacher teacher = scenario.clara();
        Student matteo = scenario.student(teacher, "Matteo Mafra", "Piano");
        student = matteo.id();
        lesson =
                scenario.approvedLesson(teacher, matteo, LocalDate.of(2026, 3, 10), "Sopro").id();
        administrator = signInNewAdministrator();
        clara = signInTeacher(ScenarioIT.CODE_CLARA);
        semester = approvedSemester(clara, student, "2026-1");
    }

    private List<String> schoolRoutes() {
        return List.of(
                SCHOOL_HISTORY,
                SCHOOL_HISTORY + "?studentId=" + student,
                lessonPdf(lesson),
                lessonDownload(lesson),
                semesterPdf(semester),
                semesterDownload(semester));
    }

    private List<String> teacherRoutes() {
        return List.of(
                "/api/v1/lesson-reports/" + lesson + "/official-pdf",
                "/api/v1/lesson-reports/" + lesson + "/versions/1/pdf",
                "/api/v1/semester-reports/" + semester + "/official-pdf",
                "/api/v1/semester-reports/" + semester + "/versions/1/pdf",
                "/api/v1/students/" + student + "/history");
    }

    /** Status, corpo e cabeçalhos relevantes (sem o carimbo de tempo nem cookies). */
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

    private MockHttpServletResponse get(String path, Cookie cookie) throws Exception {
        return call(HttpMethod.GET, path, cookie);
    }

    private String digest() {
        StringBuilder digest = new StringBuilder();
        for (String table : List.of("lesson_report", "semester_report", "report_version")) {
            digest.append(
                    jdbc.queryForObject(
                            "select coalesce(md5(string_agg(t::text, '|' order by t::text)), '-')"
                                    + " from "
                                    + table
                                    + " t",
                            String.class));
        }
        return digest.toString();
    }

    @Test
    void teacherCredentialIncludingTheAuthorGetsTheUnknownRouteAnswerOnEverySchoolRoute()
            throws Exception {
        String unknown = fingerprint(get(UNKNOWN_ADMIN_ROUTE, clara));
        assertThat(unknown).startsWith("404");

        List<String> differences = new ArrayList<>();
        for (String route : schoolRoutes()) {
            MockHttpServletResponse response = get(route, clara);
            if (!fingerprint(response).equals(unknown)) {
                differences.add(route + " -> " + response.getStatus());
            }
            assertThat(new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotStartWith("%PDF");
        }
        assertThat(differences).as("rotas que diferem da rota inexistente").isEmpty();
    }

    @Test
    void administratorCredentialAloneReachesNoTeacherReportRoute() throws Exception {
        String unknown = fingerprint(get(UNKNOWN_TEACHER_ROUTE, administrator));
        assertThat(unknown).startsWith("404");

        List<String> differences = new ArrayList<>();
        for (String route : teacherRoutes()) {
            MockHttpServletResponse response = get(route, administrator);
            if (!fingerprint(response).equals(unknown)) {
                differences.add(route + " -> " + response.getStatus());
            }
            assertThat(new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotStartWith("%PDF");
        }
        assertThat(differences).as("rotas do professor que diferem da inexistente").isEmpty();
    }

    @Test
    void withoutSessionEverySchoolRouteIsUnauthorized() throws Exception {
        for (String route : schoolRoutes()) {
            MockHttpServletResponse response = get(route, null);
            assertThat(response.getStatus()).as(route).isEqualTo(401);
            assertThat(new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotStartWith("%PDF");
        }
    }

    @Test
    void expiredSessionIsUnauthorized() throws Exception {
        clock.advance(Duration.ofHours(2).plusMinutes(1));

        for (String route : schoolRoutes()) {
            MockHttpServletResponse response = get(route, administrator);
            assertThat(response.getStatus()).as(route).isEqualTo(401);
            assertThat(new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotStartWith("%PDF");
        }
    }

    @Test
    void afterSigningOutTheSameCookieIsUnauthorized() throws Exception {
        assertThat(get(lessonPdf(lesson), administrator).getStatus()).isEqualTo(200);

        MockHttpServletResponse signOut = call(HttpMethod.DELETE, ADMIN + "/session", administrator);
        assertThat(signOut.getStatus()).isBetween(200, 204);

        for (String route : schoolRoutes()) {
            MockHttpServletResponse response = get(route, administrator);
            assertThat(response.getStatus()).as(route).isEqualTo(401);
            assertThat(new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotStartWith("%PDF");
        }
    }

    @Test
    void thereIsNoRouteByVersionAndTheAnswerIsTheUnknownRouteOne() throws Exception {
        String unknown = fingerprint(get(UNKNOWN_ADMIN_ROUTE, administrator));
        assertThat(unknown).startsWith("404");

        for (String route :
                List.of(
                        SCHOOL_HISTORY + "/lesson-reports/" + lesson + "/versions/1/pdf",
                        SCHOOL_HISTORY + "/semester-reports/" + semester + "/versions/1/pdf",
                        SCHOOL_HISTORY + "/lesson-reports/" + lesson + "/versions/1/pdf/download")) {
            assertThat(fingerprint(get(route, administrator))).as(route).isEqualTo(unknown);
        }
    }

    @Test
    void writeMethodsAreNotRoutesAndChangeNothing() throws Exception {
        String before = digest();
        String unknown = fingerprint(get(UNKNOWN_ADMIN_ROUTE, administrator));

        for (HttpMethod method :
                List.of(HttpMethod.POST, HttpMethod.PATCH, HttpMethod.PUT, HttpMethod.DELETE)) {
            for (String route : schoolRoutes()) {
                String body = method == HttpMethod.DELETE ? null : "{}";
                MockHttpServletResponse response = call(method, route, administrator, body);
                assertThat(response.getStatus()).as(method + " " + route).isEqualTo(404);
                assertThat(errorCode(response)).isEqualTo("RESOURCE_NOT_FOUND");
                assertThat(response.getContentAsString())
                        .as(method + " " + route)
                        .isEqualTo(
                                get(UNKNOWN_ADMIN_ROUTE, administrator).getContentAsString());
            }
        }

        assertThat(unknown).startsWith("404");
        assertThat(digest()).as("nenhum relatório mudou").isEqualTo(before);
    }
}
