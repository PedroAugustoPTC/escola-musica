package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * Só relatórios aprovados aparecem na visão da escola (SC-002) e a lista do administrador para um
 * aluno é exatamente a união dos históricos que cada professor vê dele (SC-010). Postgres real, por
 * HTTP. O aluno tem relatórios de aula em todos os estados não aprovados (cada um de um professor
 * diferente, porque só pode haver um relatório de aula aberto por professor e aluno).
 */
class SchoolHistoryOnlyApprovedIT extends SchoolHistoryBase {

    private Student student;
    private Cookie administrator;
    private Cookie clara;
    private Cookie bruno;
    private UUID claraLesson;
    private UUID brunoLesson;
    private UUID claraSemester;

    @BeforeEach
    void prepare() throws Exception {
        Teacher claraTeacher = scenario.clara();
        Teacher brunoTeacher = scenario.bruno();
        student = scenario.student(claraTeacher, "Matteo Mafra", "Piano");
        assign(brunoTeacher, student);

        claraLesson =
                scenario.approvedLesson(claraTeacher, student, LocalDate.of(2026, 3, 10), "Escala")
                        .id();
        brunoLesson =
                scenario.approvedLesson(brunoTeacher, student, LocalDate.of(2026, 5, 20), "Estudo")
                        .id();

        // um relatório de aula em cada estado não aprovado
        Map<String, Teacher> others = new LinkedHashMap<>();
        others.put("PROCESSING", scenario.teacher("Dora Tavares", "dora@example.test", "DRKB2XPA"));
        others.put(
                "PROCESSING_FAILED",
                scenario.teacher("Evandro Nunes", "evandro@example.test", "EVAN2XPA"));
        others.put(
                "AWAITING_ANSWERS", scenario.teacher("Fabia Reis", "fabia@example.test", "FABK2XPA"));
        others.put(
                "AWAITING_REVIEW", scenario.teacher("Gilda Rocha", "gilda@example.test", "GLRK2XPA"));
        int day = 1;
        for (Map.Entry<String, Teacher> entry : others.entrySet()) {
            assign(entry.getValue(), student);
            UUID report =
                    scenario.approvedLesson(
                                    entry.getValue(),
                                    student,
                                    LocalDate.of(2026, 4, day++),
                                    "Em andamento")
                            .id();
            revertLessonTo(report, entry.getKey());
        }
        UUID cancelled =
                scenario.approvedLesson(claraTeacher, student, LocalDate.of(2026, 4, 20), "x").id();

        administrator = signInNewAdministrator();
        clara = signInTeacher(ScenarioIT.CODE_CLARA);
        bruno = signInTeacher(ScenarioIT.CODE_BRUNO);
        claraSemester = approvedSemester(clara, student.id(), "2026-1");
        // semestral do outro professor ainda aguardando conferência: não é aprovado
        generatedSemester(bruno, student.id(), "2026-1");
        revertLessonTo(cancelled, "CANCELLED");
    }

    private Set<UUID> reportIds(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        Set<UUID> ids = new HashSet<>();
        for (JsonNode item : body(response).path("items")) {
            ids.add(UUID.fromString(item.path("reportId").asString()));
        }
        return ids;
    }

    @Test
    void listAndTotalHaveOnlyApprovedReports() throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.GET,
                        SCHOOL_HISTORY + "?studentId=" + student.id() + "&size=100",
                        administrator);

        assertThat(reportIds(response))
                .containsExactlyInAnyOrder(claraLesson, brunoLesson, claraSemester);
        assertThat(body(response).path("total").asLong()).isEqualTo(3);
        assertThat(body(response).path("items")).hasSize(3);
    }

    @Test
    void theAdministratorsListIsTheUnionOfWhatEachTeacherSeesOfTheStudent() throws Exception {
        Set<UUID> union = new HashSet<>();
        for (Cookie teacher : List.of(clara, bruno)) {
            union.addAll(
                    reportIds(
                            call(
                                    HttpMethod.GET,
                                    "/api/v1/students/" + student.id() + "/history",
                                    teacher)));
        }

        MockHttpServletResponse response =
                call(
                        HttpMethod.GET,
                        SCHOOL_HISTORY + "?studentId=" + student.id() + "&size=100",
                        administrator);

        assertThat(union).hasSize(3);
        assertThat(reportIds(response)).isEqualTo(union);
    }

    @Test
    void pagesHaveOnlyApprovedReportsAndTheSameTotal() throws Exception {
        Set<UUID> seen = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            MockHttpServletResponse response =
                    call(
                            HttpMethod.GET,
                            SCHOOL_HISTORY + "?studentId=" + student.id() + "&size=1&page=" + page,
                            administrator);
            assertThat(body(response).path("total").asLong()).isEqualTo(3);
            seen.addAll(reportIds(response));
        }

        assertThat(seen).containsExactlyInAnyOrder(claraLesson, brunoLesson, claraSemester);
    }
}
