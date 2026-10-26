package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.InactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.ReactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contrato de {@code /my-students/**} (feature 005, US4; FR-024 a FR-026) contra {@code
 * contracts/api-administration.openapi.yaml}. Os casos de uso reais são da fatia S2: aqui eles são
 * dublês, e o teste cobre a camada web (sessão do professor, tradução de erros, forma dos corpos).
 * O escopo e a permissão são decididos no núcleo; o adaptador só leva o ator da sessão (canal Web).
 */
class MyStudentContractIT extends ContractApiBase {

    private static final String CPF_DIGITS = "52998224725";
    private static final String CPF_MASKED = "529.982.247-25";
    private static final UUID STUDENT_ID = UUID.fromString("7f4c1d3e-2a5b-4c6d-8e9f-0a1b2c3d4e5f");
    private static final UUID OTHER_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    private static final OpenApiInteractionValidator CONTRACT = createContractValidator();

    @MockitoBean ListStudentsUseCase listStudents;
    @MockitoBean GetStudentUseCase getStudent;
    @MockitoBean RegisterStudentUseCase registerStudent;
    @MockitoBean UpdateStudentUseCase updateStudent;
    @MockitoBean InactivateStudentUseCase inactivateStudent;
    @MockitoBean ReactivateStudentUseCase reactivateStudent;

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger root;
    private Level rootLevel;
    private Cookie session;
    private UUID teacherId;

    @BeforeEach
    void load() throws Exception {
        teacherId = scenario.clara().id();
        session = signIn(ScenarioIT.CODE_CLARA);
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLevel = root.getLevel();
        root.setLevel(Level.INFO);
        events.start();
        root.addAppender(events);
    }

    @AfterEach
    void restoreLogs() {
        root.detachAppender(events);
        events.stop();
        root.setLevel(rootLevel);
    }

    private static OpenApiInteractionValidator createContractValidator() {
        String configured = System.getenv("CONTRACT_ADMINISTRATION_API");
        Path path =
                configured != null && Files.exists(Path.of(configured))
                        ? Path.of(configured)
                        : Path.of(
                                "../../specs/005-admin-registrations-access/contracts/"
                                        + "api-administration.openapi.yaml");
        return OpenApiInteractionValidator.createFor(readContract(path))
                .withLevelResolver(
                        LevelResolver.create()
                                // a ausência do cookie é justamente o cenário de 401
                                .withLevel(
                                        "validation.request.security.missing",
                                        ValidationReport.Level.IGNORE)
                                .withLevel(
                                        "validation.request.parameter.header.missing",
                                        ValidationReport.Level.IGNORE)
                                .build())
                .build();
    }

    /**
     * Lê o contrato e põe entre aspas as descrições de mapa em linha que têm vírgula sem aspas (o
     * validador as recusa como YAML inválido). O arquivo do contrato não é alterado.
     */
    private static String readContract(Path path) {
        try {
            String text = Files.readString(path);
            return Pattern.compile("\\{ description: ([^\"{}\\n]*,[^\"{}\\n]*?) \\}")
                    .matcher(text)
                    .replaceAll(
                            m ->
                                    "{ description: \""
                                            + Matcher.quoteReplacement(m.group(1))
                                            + "\" }");
        } catch (IOException error) {
            throw new IllegalStateException("contrato ilegível", error);
        }
    }

    /** Requisição e resposta seguem o contrato da 005. */
    private static ResultMatcher followsAdministrationContract() {
        return OpenApiValidationMatchers.openApi().isValid(CONTRACT);
    }

    private static StudentView view(UUID id, String name, boolean canInactivate) {
        return new StudentView(
                id,
                name,
                LocalDate.of(2012, 3, 4),
                14,
                "Piano",
                true,
                new StudentView.GuardianView("Marta Prado", "11987654321", "marta@example.com"),
                List.of(),
                canInactivate);
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request) {
        return request.cookie(session).with(csrf().asHeader());
    }

    private static DomainException failure(ErrorCode code) {
        return new DomainException(code);
    }

    private static DomainException blocked(int seconds) {
        return new DomainException(
                ErrorCode.OPERATION_TEMPORARILY_BLOCKED, Map.of("remainingSeconds", seconds));
    }

    private static final String NEW_STUDENT =
            """
            {"name":"Helena Prado","birthDate":"2012-03-04","cpf":"529.982.247-25",
             "guardian":{"name":"Marta Prado","phone":"(11) 98765-4321","email":"marta@example.com"},
             "instrument":"Piano"}""";

    // ---------------------------------------------------------------- listagem

    @Test
    void listUsesTheSessionTeacherAndTheContractDefaults() throws Exception {
        when(listStudents.run(any(), any()))
                .thenReturn(
                        new PageResult<>(
                                List.of(view(STUDENT_ID, "Helena Prado", true)), 0, 20, 1));

        MvcResult result =
                mvc.perform(get("/api/v1/my-students").cookie(session))
                        .andExpect(status().isOk())
                        .andExpect(followsAdministrationContract())
                        .andExpect(
                                header().string(
                                                "Cache-Control",
                                                org.hamcrest.Matchers.containsString("no-store")))
                        .andExpect(jsonPath("$.items", hasSize(1)))
                        .andExpect(jsonPath("$.items[0].id").value(STUDENT_ID.toString()))
                        .andExpect(jsonPath("$.items[0].name").value("Helena Prado"))
                        .andExpect(jsonPath("$.items[0].age").value(14))
                        .andExpect(jsonPath("$.items[0].active").value(true))
                        .andExpect(jsonPath("$.items[0].canInactivate").value(true))
                        .andExpect(jsonPath("$.items[0].guardian.phone").value("11987654321"))
                        .andExpect(jsonPath("$.items[0].teachers").doesNotExist())
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(20))
                        .andExpect(jsonPath("$.total").value(1))
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());

        var actor = ArgumentCaptor.forClass(Actor.class);
        var command = ArgumentCaptor.forClass(ListStudentsUseCase.Command.class);
        verify(listStudents).run(actor.capture(), command.capture());
        assertThat(actor.getValue())
                .isEqualTo(new Actor(Role.TEACHER, teacherId, ActionOrigin.WEB, false));
        assertThat(command.getValue().search()).isNull();
        assertThat(command.getValue().status()).isEqualTo(StudentStatusFilter.ACTIVE);
        assertThat(command.getValue().page().page()).isZero();
        assertThat(command.getValue().page().size()).isEqualTo(20);
    }

    @Test
    void listForwardsSearchStatusAndPagination() throws Exception {
        when(listStudents.run(any(), any())).thenReturn(new PageResult<>(List.of(), 1, 5, 0));

        mvc.perform(
                        get("/api/v1/my-students")
                                .param("search", "hel")
                                .param("status", "INACTIVE")
                                .param("page", "1")
                                .param("size", "5")
                                .cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsAdministrationContract())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));

        var command = ArgumentCaptor.forClass(ListStudentsUseCase.Command.class);
        verify(listStudents).run(any(), command.capture());
        assertThat(command.getValue().search()).isEqualTo("hel");
        assertThat(command.getValue().status()).isEqualTo(StudentStatusFilter.INACTIVE);
        assertThat(command.getValue().page().page()).isEqualTo(1);
        assertThat(command.getValue().page().size()).isEqualTo(5);
    }

    @Test
    void listWithInvalidPaginationOrStatusIs400() throws Exception {
        mvc.perform(get("/api/v1/my-students").param("size", "0").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/my-students").param("page", "-1").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/my-students").param("status", "SOME").cookie(session))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listStudents);
    }

    @Test
    void searchShapedLikeCpfIsRefusedByTheCoreWithoutEchoingTheTerm() throws Exception {
        when(listStudents.run(any(), any()))
                .thenThrow(
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
                mvc.perform(get("/api/v1/my-students").param("search", CPF_MASKED).cookie(session))
                        .andExpect(status().isBadRequest())
                        // o contrato não declara 400 em GET /my-students (lacuna relatada ao
                        // coordenador)
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.details.fields[0].field").value("search"))
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());
        assertThat(logText()).doesNotContain(CPF_MASKED).doesNotContain(CPF_DIGITS);
    }

    @Test
    void listBlockedByTheCpfLookupLimitIs429WithRemainingTime() throws Exception {
        when(listStudents.run(any(), any())).thenThrow(blocked(1800));

        mvc.perform(get("/api/v1/my-students").cookie(session))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1800"))
                .andExpect(jsonPath("$.code").value("OPERATION_TEMPORARILY_BLOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(1800))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Esta operação está indisponível no momento. Tente"
                                                + " novamente em 30 minuto(s)."));
    }

    // ---------------------------------------------------------------- cadastro

    @Test
    void registerCreatesWithTheSessionTeacherOnlyAndNeverReturnsCpf() throws Exception {
        when(registerStudent.run(any(), any())).thenReturn(view(STUDENT_ID, "Helena Prado", true));

        MvcResult result =
                mvc.perform(
                                signed(post("/api/v1/my-students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(NEW_STUDENT))
                        .andExpect(status().isCreated())
                        .andExpect(followsAdministrationContract())
                        .andExpect(jsonPath("$.id").value(STUDENT_ID.toString()))
                        .andExpect(jsonPath("$.birthDate").value("2012-03-04"))
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());

        var actor = ArgumentCaptor.forClass(Actor.class);
        var command = ArgumentCaptor.forClass(RegisterStudentUseCase.Command.class);
        verify(registerStudent).run(actor.capture(), command.capture());
        assertThat(actor.getValue())
                .isEqualTo(new Actor(Role.TEACHER, teacherId, ActionOrigin.WEB, false));
        var input = command.getValue().input();
        assertThat(input.name()).isEqualTo("Helena Prado");
        assertThat(input.birthDate()).isEqualTo("2012-03-04");
        assertThat(input.cpf().value()).isEqualTo(CPF_MASKED);
        assertThat(input.guardian().name()).isEqualTo("Marta Prado");
        assertThat(input.guardian().phone()).isEqualTo("(11) 98765-4321");
        assertThat(input.guardian().email()).isEqualTo("marta@example.com");
        assertThat(input.instrument()).isEqualTo("Piano");
        assertThat(command.getValue().teacherIds()).isEmpty();
        assertThat(command.getValue().correlationId()).isNotBlank();
        assertThat(logText()).doesNotContain(CPF_MASKED).doesNotContain(CPF_DIGITS);
    }

    @Test
    void registerIgnoresTeacherIdsSentByTheClient() throws Exception {
        when(registerStudent.run(any(), any())).thenReturn(view(STUDENT_ID, "Helena Prado", true));

        mvc.perform(
                        signed(post("/api/v1/my-students"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Helena Prado\",\"birthDate\":\"2012-03-04\","
                                                + "\"cpf\":\"52998224725\",\"teacherIds\":[\""
                                                + OTHER_ID
                                                + "\"],\"teacherId\":\""
                                                + OTHER_ID
                                                + "\"}"))
                .andExpect(status().isCreated());

        var command = ArgumentCaptor.forClass(RegisterStudentUseCase.Command.class);
        verify(registerStudent).run(any(), command.capture());
        assertThat(command.getValue().teacherIds()).isEmpty();
    }

    @Test
    void registerWithInvalidFieldsIs400WithFieldCodesAndNoCpf() throws Exception {
        when(registerStudent.run(any(), any()))
                .thenThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of("field", "cpf", "code", "INVALID_CPF"),
                                                Map.of(
                                                        "field",
                                                        "guardian",
                                                        "code",
                                                        "GUARDIAN_REQUIRED")))));

        MvcResult result =
                mvc.perform(
                                signed(post("/api/v1/my-students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(NEW_STUDENT))
                        .andExpect(status().isBadRequest())
                        .andExpect(followsAdministrationContract())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.details.fields", hasSize(2)))
                        .andExpect(jsonPath("$.details.fields[0].code").value("INVALID_CPF"))
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());
    }

    @Test
    void registerWithUnreadableBodyIs400WithoutEchoingIt() throws Exception {
        MvcResult result =
                mvc.perform(
                                signed(post("/api/v1/my-students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"cpf\":\"" + CPF_MASKED + "\", oops"))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());
        verifyNoInteractions(registerStudent);
    }

    @Test
    void registerWithCpfOfAnotherTeachersStudentIsNeutralConflict() throws Exception {
        when(registerStudent.run(any(), any())).thenThrow(failure(ErrorCode.CPF_UNAVAILABLE));

        MvcResult result =
                mvc.perform(
                                signed(post("/api/v1/my-students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(NEW_STUDENT))
                        .andExpect(status().isConflict())
                        .andExpect(followsAdministrationContract())
                        .andExpect(jsonPath("$.code").value("CPF_UNAVAILABLE"))
                        .andExpect(
                                jsonPath("$.message")
                                        .value(
                                                "Não foi possível usar este CPF. Procure o administrador."))
                        .andExpect(jsonPath("$.details").doesNotExist())
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());
    }

    @Test
    void registerWithCpfOfOwnStudentIsConflictStudentAlreadyYours() throws Exception {
        when(registerStudent.run(any(), any())).thenThrow(failure(ErrorCode.STUDENT_ALREADY_YOURS));

        mvc.perform(
                        signed(post("/api/v1/my-students"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(NEW_STUDENT))
                .andExpect(status().isConflict())
                .andExpect(followsAdministrationContract())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_YOURS"))
                .andExpect(jsonPath("$.message").value("Este aluno já está entre os seus."));
    }

    @Test
    void registerBlockedByCpfProbeLimitIs429WithRemainingTime() throws Exception {
        when(registerStudent.run(any(), any())).thenThrow(blocked(600));

        mvc.perform(
                        signed(post("/api/v1/my-students"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(NEW_STUDENT))
                .andExpect(status().isTooManyRequests())
                .andExpect(followsAdministrationContract())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.code").value("OPERATION_TEMPORARILY_BLOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(600))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Esta operação está indisponível no momento. Tente"
                                                + " novamente em 10 minuto(s)."));
    }

    // ---------------------------------------------------------------- abrir

    @Test
    void getOpensTheStudentOfTheSessionTeacher() throws Exception {
        when(getStudent.run(any(), any())).thenReturn(view(STUDENT_ID, "Helena Prado", false));

        MvcResult result =
                mvc.perform(get("/api/v1/my-students/{id}", STUDENT_ID).cookie(session))
                        .andExpect(status().isOk())
                        .andExpect(followsAdministrationContract())
                        .andExpect(jsonPath("$.id").value(STUDENT_ID.toString()))
                        .andExpect(jsonPath("$.canInactivate").value(false))
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());

        var actor = ArgumentCaptor.forClass(Actor.class);
        var command = ArgumentCaptor.forClass(GetStudentUseCase.Command.class);
        verify(getStudent).run(actor.capture(), command.capture());
        assertThat(actor.getValue())
                .isEqualTo(new Actor(Role.TEACHER, teacherId, ActionOrigin.WEB, false));
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT_ID);
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void otherTeachersStudentAndNonexistentStudentGetTheSameNotFound() throws Exception {
        when(getStudent.run(any(), any())).thenThrow(failure(ErrorCode.RESOURCE_NOT_FOUND));

        MvcResult alien =
                mvc.perform(get("/api/v1/my-students/{id}", STUDENT_ID).cookie(session))
                        .andExpect(status().isNotFound())
                        .andExpect(followsAdministrationContract())
                        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                        .andReturn();
        MvcResult nonexistent =
                mvc.perform(get("/api/v1/my-students/{id}", OTHER_ID).cookie(session))
                        .andExpect(status().isNotFound())
                        .andReturn();
        assertSameResponse(alien, nonexistent);
    }

    // ---------------------------------------------------------------- editar

    @Test
    void patchForwardsOnlyTheSentFieldsAndKeepsTheRestAbsent() throws Exception {
        when(updateStudent.run(any(), any())).thenReturn(view(STUDENT_ID, "Helena P. Prado", true));

        MvcResult result =
                mvc.perform(
                                signed(patch("/api/v1/my-students/{id}", STUDENT_ID))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"Helena P. Prado\",\"cpf\":\"\"}"))
                        .andExpect(status().isOk())
                        .andExpect(followsAdministrationContract())
                        .andExpect(jsonPath("$.name").value("Helena P. Prado"))
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());

        var actor = ArgumentCaptor.forClass(Actor.class);
        var command = ArgumentCaptor.forClass(UpdateStudentUseCase.Command.class);
        verify(updateStudent).run(actor.capture(), command.capture());
        assertThat(actor.getValue())
                .isEqualTo(new Actor(Role.TEACHER, teacherId, ActionOrigin.WEB, false));
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT_ID);
        var input = command.getValue().input();
        assertThat(input.name()).isEqualTo("Helena P. Prado");
        assertThat(input.birthDate()).isNull();
        assertThat(input.guardian()).isNull();
        assertThat(input.instrument()).isNull();
        // CPF em branco chega ao núcleo, que o trata como "manter o atual"
        assertThat(input.cpf().value()).isEmpty();
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void patchWithoutCpfSendsNoCpfAndReplacesTheGuardianAsAWhole() throws Exception {
        when(updateStudent.run(any(), any())).thenReturn(view(STUDENT_ID, "Helena Prado", true));

        mvc.perform(
                        signed(patch("/api/v1/my-students/{id}", STUDENT_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"guardian\":{\"name\":\"Rita Prado\",\"phone\":"
                                                + "\"11 3333-4444\",\"email\":\"rita@example.com\"}}"))
                .andExpect(status().isOk())
                .andExpect(followsAdministrationContract());

        var command = ArgumentCaptor.forClass(UpdateStudentUseCase.Command.class);
        verify(updateStudent).run(any(), command.capture());
        var input = command.getValue().input();
        assertThat(input.cpf()).isNull();
        assertThat(input.name()).isNull();
        assertThat(input.guardian().name()).isEqualTo("Rita Prado");
        assertThat(input.guardian().phone()).isEqualTo("11 3333-4444");
        assertThat(input.guardian().email()).isEqualTo("rita@example.com");
    }

    @Test
    void patchOfOtherTeachersStudentIsTheSameNotFound() throws Exception {
        when(updateStudent.run(any(), any())).thenThrow(failure(ErrorCode.RESOURCE_NOT_FOUND));
        when(getStudent.run(any(), any())).thenThrow(failure(ErrorCode.RESOURCE_NOT_FOUND));

        MvcResult patched =
                mvc.perform(
                                signed(patch("/api/v1/my-students/{id}", STUDENT_ID))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"Fulano\"}"))
                        .andExpect(status().isNotFound())
                        .andExpect(followsAdministrationContract())
                        .andReturn();
        MvcResult opened =
                mvc.perform(get("/api/v1/my-students/{id}", STUDENT_ID).cookie(session))
                        .andReturn();
        assertSameResponse(patched, opened);
    }

    @Test
    void patchWithCpfOfAnotherStudentIsNeutralConflictOrAlreadyYours() throws Exception {
        when(updateStudent.run(any(), any()))
                .thenThrow(failure(ErrorCode.CPF_UNAVAILABLE))
                .thenThrow(failure(ErrorCode.STUDENT_ALREADY_YOURS));

        mvc.perform(
                        signed(patch("/api/v1/my-students/{id}", STUDENT_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cpf\":\"" + CPF_MASKED + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsAdministrationContract())
                .andExpect(jsonPath("$.code").value("CPF_UNAVAILABLE"))
                .andExpect(jsonPath("$.details").doesNotExist());
        mvc.perform(
                        signed(patch("/api/v1/my-students/{id}", STUDENT_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cpf\":\"" + CPF_MASKED + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsAdministrationContract())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_YOURS"));
        assertThat(logText()).doesNotContain(CPF_MASKED).doesNotContain(CPF_DIGITS);
    }

    @Test
    void patchBlockedByCpfProbeLimitIs429() throws Exception {
        when(updateStudent.run(any(), any())).thenThrow(blocked(90));

        mvc.perform(
                        signed(patch("/api/v1/my-students/{id}", STUDENT_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cpf\":\"" + CPF_DIGITS + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(followsAdministrationContract())
                .andExpect(header().string("Retry-After", "90"))
                .andExpect(jsonPath("$.code").value("OPERATION_TEMPORARILY_BLOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(90));
    }

    // ---------------------------------------------------- inativar e reativar

    @Test
    void inactivationAndReactivationReturnTheStudent() throws Exception {
        when(inactivateStudent.run(any(), any()))
                .thenReturn(
                        new StudentView(
                                STUDENT_ID,
                                "Helena Prado",
                                LocalDate.of(2012, 3, 4),
                                14,
                                "Piano",
                                false,
                                null,
                                List.of(),
                                true));
        when(reactivateStudent.run(any(), any()))
                .thenReturn(view(STUDENT_ID, "Helena Prado", true));

        mvc.perform(signed(post("/api/v1/my-students/{id}/inactivation", STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(followsAdministrationContract())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.guardian").doesNotExist());
        mvc.perform(signed(post("/api/v1/my-students/{id}/reactivation", STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(followsAdministrationContract())
                .andExpect(jsonPath("$.active").value(true));

        var actor = ArgumentCaptor.forClass(Actor.class);
        var inactivate = ArgumentCaptor.forClass(InactivateStudentUseCase.Command.class);
        verify(inactivateStudent).run(actor.capture(), inactivate.capture());
        assertThat(actor.getValue())
                .isEqualTo(new Actor(Role.TEACHER, teacherId, ActionOrigin.WEB, false));
        assertThat(inactivate.getValue().studentId()).isEqualTo(STUDENT_ID);
        assertThat(inactivate.getValue().correlationId()).isNotBlank();
        var reactivate = ArgumentCaptor.forClass(ReactivateStudentUseCase.Command.class);
        verify(reactivateStudent).run(any(), reactivate.capture());
        assertThat(reactivate.getValue().studentId()).isEqualTo(STUDENT_ID);
        assertThat(reactivate.getValue().correlationId()).isNotBlank();
    }

    @Test
    void sharedStudentCannotBeInactivatedNorReactivatedByTheTeacher() throws Exception {
        when(inactivateStudent.run(any(), any()))
                .thenThrow(failure(ErrorCode.SHARED_STUDENT_ACTION_DENIED));
        when(reactivateStudent.run(any(), any()))
                .thenThrow(failure(ErrorCode.SHARED_STUDENT_ACTION_DENIED));

        for (String action : List.of("inactivation", "reactivation")) {
            mvc.perform(signed(post("/api/v1/my-students/{id}/" + action, STUDENT_ID)))
                    .andExpect(status().isForbidden())
                    .andExpect(followsAdministrationContract())
                    .andExpect(jsonPath("$.code").value("SHARED_STUDENT_ACTION_DENIED"))
                    .andExpect(
                            jsonPath("$.message")
                                    .value(
                                            "Este aluno tem mais de um professor. Procure o"
                                                    + " administrador para inativá-lo ou"
                                                    + " reativá-lo."));
        }
    }

    @Test
    void inactivationAndReactivationOfOtherTeachersStudentAreTheSameNotFound() throws Exception {
        when(inactivateStudent.run(any(), any())).thenThrow(failure(ErrorCode.RESOURCE_NOT_FOUND));
        when(reactivateStudent.run(any(), any())).thenThrow(failure(ErrorCode.RESOURCE_NOT_FOUND));
        when(getStudent.run(any(), any())).thenThrow(failure(ErrorCode.RESOURCE_NOT_FOUND));

        MvcResult opened =
                mvc.perform(get("/api/v1/my-students/{id}", STUDENT_ID).cookie(session))
                        .andReturn();
        for (String action : List.of("inactivation", "reactivation")) {
            MvcResult result =
                    mvc.perform(signed(post("/api/v1/my-students/{id}/" + action, STUDENT_ID)))
                            .andExpect(status().isNotFound())
                            .andExpect(followsAdministrationContract())
                            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                            .andReturn();
            assertSameResponse(opened, result);
        }
    }

    // ---------------------------------------------------- credencial e CSRF

    @Test
    void withoutSessionEveryRouteIs401AndNothingReachesTheCore() throws Exception {
        String id = STUDENT_ID.toString();
        List<MockHttpServletRequestBuilder> requests =
                List.of(
                        get("/api/v1/my-students"),
                        post("/api/v1/my-students")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(NEW_STUDENT),
                        get("/api/v1/my-students/" + id),
                        patch("/api/v1/my-students/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"),
                        post("/api/v1/my-students/" + id + "/inactivation"),
                        post("/api/v1/my-students/" + id + "/reactivation"));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request.with(csrf().asHeader()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        }
        verifyNoInteractions(
                listStudents,
                getStudent,
                registerStudent,
                updateStudent,
                inactivateStudent,
                reactivateStudent);
    }

    @Test
    void writesWithoutCsrfTokenAreRefusedBeforeTheCore() throws Exception {
        mvc.perform(
                        post("/api/v1/my-students")
                                .cookie(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(NEW_STUDENT))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/api/v1/my-students/{id}", STUDENT_ID)
                                .cookie(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/my-students/{id}/inactivation", STUDENT_ID).cookie(session))
                .andExpect(status().isForbidden());
        verifyNoInteractions(registerStudent, updateStudent, inactivateStudent);
    }

    @Test
    void unexpectedFailureIs500WithoutLeakingTheRequestData() throws Exception {
        when(registerStudent.run(any(), any()))
                .thenThrow(new IllegalStateException("falha com " + CPF_MASKED));

        MvcResult result =
                mvc.perform(
                                signed(post("/api/v1/my-students"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(NEW_STUDENT))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                        .andReturn();
        assertNoCpf(result.getResponse().getContentAsString());
        assertThat(logText()).doesNotContain(CPF_MASKED).doesNotContain(CPF_DIGITS);
    }

    // ---------------------------------------------------------------- apoio

    private static void assertNoCpf(String body) {
        assertThat(body)
                .doesNotContain(CPF_MASKED)
                .doesNotContain(CPF_DIGITS)
                .doesNotContainIgnoringCase("\"cpf\":");
    }

    private static void assertSameResponse(MvcResult first, MvcResult second) throws Exception {
        assertThat(second.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(second.getResponse().getContentType())
                .isEqualTo(first.getResponse().getContentType());
        for (String name : first.getResponse().getHeaderNames()) {
            if (name.equalsIgnoreCase("Set-Cookie")) {
                continue; // o token de CSRF muda a cada resposta
            }
            assertThat(second.getResponse().getHeaders(name))
                    .as("cabeçalho %s", name)
                    .isEqualTo(first.getResponse().getHeaders(name));
        }
        assertThat(second.getResponse().getHeaderNames())
                .containsExactlyInAnyOrderElementsOf(
                        first.getResponse().getHeaderNames().stream().toList());
    }

    private String logText() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(events.list)) {
            text.append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) {
                text.append(event.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return text.toString();
    }
}
