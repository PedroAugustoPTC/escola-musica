package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato das rotas de alunos da área administrativa (FR-017 a FR-023): listar, buscar, filtrar,
 * paginar, criar com professores, abrir, {@code PATCH}, inativar e reativar. Nenhuma propriedade de
 * resposta se chama {@code cpf} e o CPF canário nunca volta.
 */
class AdminStudentContractIT extends AdminWebBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID TEACHER_A = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID TEACHER_B = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    private static final String CREATE_BODY =
            "{\"name\":\""
                    + CanaryStudent.NAME
                    + "\",\"birthDate\":\""
                    + CanaryStudent.BIRTH_DATE_ISO
                    + "\",\"cpf\":\""
                    + CanaryCpf.MASKED
                    + "\",\"instrument\":\""
                    + CanaryStudent.INSTRUMENT
                    + "\",\"guardian\":{\"name\":\""
                    + CanaryStudent.GUARDIAN_NAME
                    + "\",\"phone\":\""
                    + CanaryStudent.GUARDIAN_PHONE_MASKED
                    + "\",\"email\":\""
                    + CanaryStudent.GUARDIAN_EMAIL
                    + "\"},\"teacherIds\":[\""
                    + TEACHER_A
                    + "\",\""
                    + TEACHER_B
                    + "\"]}";

    private StudentView view(boolean active) {
        return new StudentView(
                STUDENT,
                CanaryStudent.NAME,
                CanaryStudent.BIRTH_DATE,
                10,
                CanaryStudent.INSTRUMENT,
                active,
                new StudentView.GuardianView(
                        CanaryStudent.GUARDIAN_NAME,
                        CanaryStudent.GUARDIAN_PHONE_DIGITS,
                        CanaryStudent.GUARDIAN_EMAIL),
                List.of(
                        new StudentView.TeacherRef(TEACHER_A, "Clara Souza", true),
                        new StudentView.TeacherRef(TEACHER_B, "Bruno Lima", false)),
                null);
    }

    private static void assertNoPropertyNamedCpf(JsonNode node) {
        List<String> names = new ArrayList<>();
        collect(node, names);
        assertThat(names).noneMatch(name -> name.toLowerCase().contains("cpf"));
    }

    private static void collect(JsonNode node, List<String> names) {
        if (node.isObject()) {
            node.propertyNames()
                    .forEach(
                            name -> {
                                names.add(name);
                                collect(node.get(name), names);
                            });
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }

    private void assertCleanOfCpf(MvcResult result) throws Exception {
        assertNoPropertyNamedCpf(JSON.readTree(result.getResponse().getContentAsString()));
        ResponseSweeper.searching(CanaryCpf.sweepValues())
                .allowing(ResponseSweeper.Place.BODY)
                .assertClean(result);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(CanaryCpf.DIGITS)
                .doesNotContain(CanaryCpf.MASKED);
    }

    @Test
    void listFiltersSearchesAndPaginatesWithActiveAsDefault() throws Exception {
        given(listStudents.run(any(), any()))
                .willReturn(new PageResult<>(List.of(view(true)), 0, 20, 1));

        MvcResult result =
                mvc.perform(
                                get(BASE + "/students")
                                        .cookie(adminCookie(ADMIN_TOKEN))
                                        .param("search", "pietra"))
                        .andExpect(status().isOk())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.items[0].id").value(STUDENT.toString()))
                        .andExpect(jsonPath("$.items[0].age").value(10))
                        .andExpect(jsonPath("$.items[0].birthDate").value("2016-03-14"))
                        .andExpect(jsonPath("$.items[0].teachers.length()").value(2))
                        .andExpect(jsonPath("$.items[0].canInactivate").doesNotExist())
                        .andExpect(header().string("Cache-Control", containsString("no-store")))
                        .andReturn();
        assertCleanOfCpf(result);

        ArgumentCaptor<ListStudentsUseCase.Command> command =
                ArgumentCaptor.forClass(ListStudentsUseCase.Command.class);
        verify(listStudents).run(any(), command.capture());
        assertThat(command.getValue().search()).isEqualTo("pietra");
        assertThat(command.getValue().status()).isEqualTo(StudentStatusFilter.ACTIVE);
        assertThat(command.getValue().page().size()).isEqualTo(20);
    }

    @Test
    void listAcceptsStatusAndPageParameters() throws Exception {
        given(listStudents.run(any(), any())).willReturn(new PageResult<>(List.of(), 2, 50, 0));

        mvc.perform(
                        get(BASE + "/students")
                                .cookie(adminCookie(ADMIN_TOKEN))
                                .param("status", "ALL")
                                .param("page", "2")
                                .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(followsContract());

        ArgumentCaptor<ListStudentsUseCase.Command> command =
                ArgumentCaptor.forClass(ListStudentsUseCase.Command.class);
        verify(listStudents).run(any(), command.capture());
        assertThat(command.getValue().status()).isEqualTo(StudentStatusFilter.ALL);
        assertThat(command.getValue().page().page()).isEqualTo(2);
        assertThat(command.getValue().page().size()).isEqualTo(50);
    }

    @Test
    void searchWithTheShapeOfACpfIsRefusedWithoutRepeatingTheTerm() throws Exception {
        given(listStudents.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "search",
                                                        "code",
                                                        "INVALID_TEXT")))));

        MvcResult result =
                mvc.perform(
                                get(BASE + "/students")
                                        .cookie(adminCookie(ADMIN_TOKEN))
                                        .param("search", CanaryCpf.DIGITS))
                        .andExpect(status().isBadRequest())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.details.fields[0].field").value("search"))
                        .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(CanaryCpf.DIGITS);
    }

    @Test
    void createWithTeachersReturns201AndPassesEverythingToTheCore() throws Exception {
        given(registerStudent.run(any(), any())).willReturn(view(true));

        MvcResult result =
                mvc.perform(
                                asAdmin(post(BASE + "/students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(CREATE_BODY))
                        .andExpect(status().isCreated())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.guardian.name").value(CanaryStudent.GUARDIAN_NAME))
                        .andExpect(jsonPath("$.guardian.phone").value("11943218765"))
                        .andExpect(jsonPath("$.teachers[1].active").value(false))
                        .andReturn();
        assertCleanOfCpf(result);

        ArgumentCaptor<RegisterStudentUseCase.Command> command =
                ArgumentCaptor.forClass(RegisterStudentUseCase.Command.class);
        verify(registerStudent).run(any(), command.capture());
        var input = command.getValue().input();
        assertThat(input.name()).isEqualTo(CanaryStudent.NAME);
        assertThat(input.birthDate()).isEqualTo(CanaryStudent.BIRTH_DATE_ISO);
        assertThat(input.cpf()).isEqualTo(new Secret(CanaryCpf.MASKED));
        assertThat(input.instrument()).isEqualTo(CanaryStudent.INSTRUMENT);
        assertThat(input.guardian().name()).isEqualTo(CanaryStudent.GUARDIAN_NAME);
        assertThat(input.guardian().phone()).isEqualTo(CanaryStudent.GUARDIAN_PHONE_MASKED);
        assertThat(input.guardian().email()).isEqualTo(CanaryStudent.GUARDIAN_EMAIL);
        assertThat(command.getValue().teacherIds()).containsExactly(TEACHER_A, TEACHER_B);
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void createWithoutTeachersSendsAnEmptyList() throws Exception {
        given(registerStudent.run(any(), any())).willReturn(view(true));

        mvc.perform(
                        asAdmin(post(BASE + "/students"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Adulto\",\"birthDate\":\"1990-01-01\",\"cpf\":\""
                                                + CanaryCpf.DIGITS
                                                + "\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterStudentUseCase.Command> command =
                ArgumentCaptor.forClass(RegisterStudentUseCase.Command.class);
        verify(registerStudent).run(any(), command.capture());
        assertThat(command.getValue().teacherIds()).isEmpty();
        assertThat(command.getValue().input().guardian()).isNull();
    }

    @Test
    void duplicateCpfNamesTheExistingStudentAndNeverRepeatsTheCpf() throws Exception {
        given(registerStudent.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.DUPLICATE_CPF, Map.of("name", "Aluno Existente")));

        MvcResult result =
                mvc.perform(
                                asAdmin(post(BASE + "/students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(CREATE_BODY))
                        .andExpect(status().isConflict())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.code").value("DUPLICATE_CPF"))
                        .andExpect(jsonPath("$.details.name").value("Aluno Existente"))
                        .andExpect(
                                jsonPath("$.message")
                                        .value("Este CPF já pertence ao aluno Aluno Existente."))
                        .andReturn();
        assertCleanOfCpf(result);
    }

    @Test
    void inactiveTeacherIs409() throws Exception {
        given(registerStudent.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.TEACHER_INACTIVE));

        mvc.perform(
                        asAdmin(post(BASE + "/students"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("TEACHER_INACTIVE"));
    }

    @Test
    void failureInTheMiddleOfRegistrationReturnsOperationNotSavedAndNothingElse() throws Exception {
        given(registerStudent.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.OPERATION_NOT_SAVED));

        MvcResult result =
                mvc.perform(
                                asAdmin(post(BASE + "/students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(CREATE_BODY))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("OPERATION_NOT_SAVED"))
                        .andExpect(jsonPath("$.reference").doesNotExist())
                        .andReturn();
        assertCleanOfCpf(result);
    }

    @Test
    void validationErrorsComeInDetailsFields() throws Exception {
        given(registerStudent.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "birthDate",
                                                        "code",
                                                        "INVALID_BIRTH_DATE"),
                                                Map.of(
                                                        "field",
                                                        "guardian",
                                                        "code",
                                                        "GUARDIAN_REQUIRED")))));

        MvcResult result =
                mvc.perform(
                                asAdmin(post(BASE + "/students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(CREATE_BODY))
                        .andExpect(status().isBadRequest())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.details.fields[0].field").value("birthDate"))
                        .andExpect(jsonPath("$.details.fields[1].code").value("GUARDIAN_REQUIRED"))
                        .andReturn();
        assertCleanOfCpf(result);
    }

    @Test
    void getReturnsAnyStudentAndUnknownIsTheStandardNotFound() throws Exception {
        given(getStudent.run(any(), any())).willReturn(view(true));
        MvcResult found =
                mvc.perform(get(BASE + "/students/" + STUDENT).cookie(adminCookie(ADMIN_TOKEN)))
                        .andExpect(status().isOk())
                        .andExpect(followsContract())
                        .andReturn();
        assertCleanOfCpf(found);
        ArgumentCaptor<GetStudentUseCase.Command> command =
                ArgumentCaptor.forClass(GetStudentUseCase.Command.class);
        verify(getStudent).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);

        given(getStudent.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get(BASE + "/students/" + UUID.randomUUID()).cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void patchSendsOnlyTheFieldsPresent() throws Exception {
        given(updateStudent.run(any(), any())).willReturn(view(true));

        mvc.perform(
                        asAdmin(patch(BASE + "/students/" + STUDENT))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"instrument\":\"Piano\"}"))
                .andExpect(status().isOk())
                .andExpect(followsContract());

        ArgumentCaptor<UpdateStudentUseCase.Command> command =
                ArgumentCaptor.forClass(UpdateStudentUseCase.Command.class);
        verify(updateStudent).run(any(), command.capture());
        var input = command.getValue().input();
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);
        assertThat(input.instrument()).isEqualTo("Piano");
        assertThat(input.name()).isNull();
        assertThat(input.birthDate()).isNull();
        assertThat(input.cpf()).isNull();
        assertThat(input.guardian()).isNull();
    }

    @Test
    void patchWithBlankCpfPassesTheBlankAndTheCoreKeepsTheCurrentOne() throws Exception {
        given(updateStudent.run(any(), any())).willReturn(view(true));

        mvc.perform(
                        asAdmin(patch(BASE + "/students/" + STUDENT))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cpf\":\"\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateStudentUseCase.Command> command =
                ArgumentCaptor.forClass(UpdateStudentUseCase.Command.class);
        verify(updateStudent).run(any(), command.capture());
        assertThat(command.getValue().input().cpf()).isEqualTo(new Secret(""));
    }

    @Test
    void inactivationAndReactivationReturnTheStudent() throws Exception {
        given(inactivateStudent.run(any(), any())).willReturn(view(false));
        given(reactivateStudent.run(any(), any())).willReturn(view(true));

        mvc.perform(asAdmin(post(BASE + "/students/" + STUDENT + "/inactivation")))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(asAdmin(post(BASE + "/students/" + STUDENT + "/reactivation")))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void cpfNeverReachesLogsOrResponseHeaders() throws Exception {
        given(registerStudent.run(any(), any())).willReturn(view(true));
        try (LogCapture log = LogCapture.start()) {
            MvcResult result =
                    mvc.perform(
                                    asAdmin(post(BASE + "/students"))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(CREATE_BODY))
                            .andReturn();
            ResponseSweeper.searching(CanaryCpf.sweepValues())
                    .allowing(ResponseSweeper.Place.BODY)
                    .assertClean(result);
            assertThat(
                            ResponseSweeper.searching(CanaryCpf.sweepValues())
                                    .sweepText(ResponseSweeper.Place.LOG, log.text()))
                    .isEmpty();
        }
    }
}
