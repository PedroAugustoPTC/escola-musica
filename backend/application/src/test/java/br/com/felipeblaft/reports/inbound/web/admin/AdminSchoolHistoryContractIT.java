package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase.Result;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase.StudentRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato da lista da visão da escola (feature 006, US1): rota, status, corpo contra {@code
 * api-school-history.openapi.yaml}, cabeçalhos e ausência de dado pessoal além do permitido.
 */
class AdminSchoolHistoryContractIT extends AdminWebBase {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID TEACHER_A = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID TEACHER_B = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID LESSON = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID SEMESTER = UUID.fromString("00000000-0000-4000-8000-0000000000d2");
    private static final String URL = BASE + "/school-history";

    private static ApprovedReportRow lessonRow() {
        return new ApprovedReportRow(
                ReportType.LESSON,
                LESSON,
                STUDENT,
                CanaryStudent.NAME,
                true,
                TEACHER_A,
                "Clara Souza",
                true,
                LocalDate.of(2026, 3, 10),
                null,
                Instant.parse("2026-03-10T21:30:00Z"),
                3);
    }

    private static ApprovedReportRow semesterRow() {
        return new ApprovedReportRow(
                ReportType.SEMESTER,
                SEMESTER,
                STUDENT,
                CanaryStudent.NAME,
                false,
                TEACHER_B,
                "Bruno Lima",
                false,
                null,
                SemesterPeriod.of("2026-1"),
                Instant.parse("2026-07-02T12:00:00Z"),
                1);
    }

    private static Result result(boolean withStudent, ApprovedReportRow... rows) {
        return new Result(
                withStudent ? new StudentRef(STUDENT, CanaryStudent.NAME, true) : null,
                new PageResult<>(List.of(rows), 0, 20, rows.length));
    }

    @Test
    void listsApprovedItemsOfAllTeachersFollowingTheContract() throws Exception {
        given(querySchoolHistory.run(any(), any()))
                .willReturn(result(true, lessonRow(), semesterRow()));

        MvcResult mvcResult =
                mvc.perform(
                                get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                        .param("studentId", STUDENT.toString()))
                        .andExpect(status().isOk())
                        .andExpect(followsSchoolHistoryContract())
                        .andExpect(header().string("Cache-Control", "no-store, private"))
                        .andExpect(jsonPath("$.student.id").value(STUDENT.toString()))
                        .andExpect(jsonPath("$.student.active").value(true))
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(20))
                        .andExpect(jsonPath("$.total").value(2))
                        .andExpect(jsonPath("$.items[0].type").value("LESSON"))
                        .andExpect(jsonPath("$.items[0].reportId").value(LESSON.toString()))
                        .andExpect(jsonPath("$.items[0].label").value("Aula de 10/03/2026"))
                        .andExpect(jsonPath("$.items[0].lessonDate").value("2026-03-10"))
                        .andExpect(jsonPath("$.items[0].periodStart").doesNotExist())
                        .andExpect(jsonPath("$.items[0].teacher.id").value(TEACHER_A.toString()))
                        .andExpect(jsonPath("$.items[0].teacher.active").value(true))
                        .andExpect(jsonPath("$.items[0].student.id").value(STUDENT.toString()))
                        .andExpect(jsonPath("$.items[0].approvedVersion").value(3))
                        .andExpect(jsonPath("$.items[1].type").value("SEMESTER"))
                        .andExpect(
                                jsonPath("$.items[1].label")
                                        .value("Semestral (01/01/2026 a 30/06/2026)"))
                        .andExpect(jsonPath("$.items[1].periodStart").value("2026-01-01"))
                        .andExpect(jsonPath("$.items[1].periodEnd").value("2026-06-30"))
                        .andExpect(jsonPath("$.items[1].lessonDate").doesNotExist())
                        .andExpect(jsonPath("$.items[1].teacher.active").value(false))
                        .andReturn();

        ArgumentCaptor<QuerySchoolHistoryUseCase.Command> command =
                ArgumentCaptor.forClass(QuerySchoolHistoryUseCase.Command.class);
        verify(querySchoolHistory).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);
        assertThat(command.getValue().page().page()).isZero();
        assertThat(command.getValue().page().size()).isEqualTo(20);
        assertThat(command.getValue().correlationId()).isNotBlank();

        Map<String, String> forbidden = new LinkedHashMap<>(CanaryStudent.sweepValuesWithCpf());
        forbidden.remove("nome do aluno canário");
        ResponseSweeper.searching(forbidden)
                .allowing(ResponseSweeper.Place.BODY)
                .assertClean(mvcResult);
        assertThat(mvcResult.getResponse().getContentAsString())
                .doesNotContain(CanaryCpf.DIGITS)
                .doesNotContain(CanaryCpf.MASKED)
                .doesNotContain("\"cpf\"")
                .doesNotContain("birthDate")
                .doesNotContain("guardian")
                .doesNotContain("email");
    }

    @Test
    void studentIsAbsentWhenTheFilterPointsToNoExistingStudent() throws Exception {
        given(querySchoolHistory.run(any(), any())).willReturn(result(false));

        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("studentId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(followsSchoolHistoryContract())
                .andExpect(jsonPath("$.student").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void passesPageAndSizeToTheUseCase() throws Exception {
        given(querySchoolHistory.run(any(), any())).willReturn(result(false));

        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("page", "2")
                                .param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<QuerySchoolHistoryUseCase.Command> command =
                ArgumentCaptor.forClass(QuerySchoolHistoryUseCase.Command.class);
        verify(querySchoolHistory).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isNull();
        assertThat(command.getValue().page().page()).isEqualTo(2);
        assertThat(command.getValue().page().size()).isEqualTo(50);
    }

    @Test
    void withoutCookieIsUnauthorized() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(followsSchoolHistoryContract());
    }
}
