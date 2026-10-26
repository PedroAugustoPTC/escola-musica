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

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.usecase.teacher.ListTeachersUseCase;
import br.com.felipeblaft.reports.usecase.teacher.RegisterTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.TeacherView;
import br.com.felipeblaft.reports.usecase.teacher.TeacherWithCode;
import br.com.felipeblaft.reports.usecase.teacher.UpdateTeacherUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato das rotas de professores da área administrativa (FR-010 a FR-015): listar e buscar,
 * criar (código só na resposta), abrir, {@code PATCH}, inativar e reativar (código novo).
 */
class AdminTeacherContractIT extends AdminWebBase {

    private static final UUID TEACHER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final String ACCESS_CODE = "K7QM2XPA";

    private TeacherView view(boolean active) {
        return new TeacherView(TEACHER, "Clara Souza", "clara@example.test", active, false);
    }

    private TeacherWithCode withCode() {
        return new TeacherWithCode(
                view(true), new Secret(ACCESS_CODE), clock.now().plusSeconds(7 * 86400));
    }

    @Test
    void listPassesSearchFilterAndPageAndNeverCarriesTheCode() throws Exception {
        given(listTeachers.run(any(), any()))
                .willReturn(new PageResult<>(List.of(view(true)), 1, 5, 11));

        MvcResult result =
                mvc.perform(
                                get(BASE + "/teachers")
                                        .cookie(adminCookie(ADMIN_TOKEN))
                                        .param("search", "clara")
                                        .param("active", "true")
                                        .param("page", "1")
                                        .param("size", "5"))
                        .andExpect(status().isOk())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.items[0].id").value(TEACHER.toString()))
                        .andExpect(jsonPath("$.items[0].name").value("Clara Souza"))
                        .andExpect(jsonPath("$.items[0].telegramLinked").value(false))
                        .andExpect(jsonPath("$.page").value(1))
                        .andExpect(jsonPath("$.size").value(5))
                        .andExpect(jsonPath("$.total").value(11))
                        .andExpect(header().string("Cache-Control", containsString("no-store")))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("accessCode")
                .doesNotContain(ACCESS_CODE);
        ArgumentCaptor<ListTeachersUseCase.Command> command =
                ArgumentCaptor.forClass(ListTeachersUseCase.Command.class);
        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(listTeachers).run(actor.capture(), command.capture());
        assertThat(actor.getValue().id()).isEqualTo(administratorId);
        assertThat(command.getValue().search()).isEqualTo("clara");
        assertThat(command.getValue().active()).isTrue();
        assertThat(command.getValue().page().page()).isEqualTo(1);
        assertThat(command.getValue().page().size()).isEqualTo(5);
    }

    @Test
    void listUsesDefaultPageWhenNothingIsSent() throws Exception {
        given(listTeachers.run(any(), any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mvc.perform(get(BASE + "/teachers").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.items").isEmpty());

        ArgumentCaptor<ListTeachersUseCase.Command> command =
                ArgumentCaptor.forClass(ListTeachersUseCase.Command.class);
        verify(listTeachers).run(any(), command.capture());
        assertThat(command.getValue().active()).isNull();
        assertThat(command.getValue().page().size()).isEqualTo(20);
    }

    @Test
    void invalidPageIs400() throws Exception {
        mvc.perform(get(BASE + "/teachers").cookie(adminCookie(ADMIN_TOKEN)).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createReturns201WithTheCodeOnlyInThisResponse() throws Exception {
        given(registerTeacher.run(any(), any())).willReturn(withCode());

        mvc.perform(
                        asAdmin(post(BASE + "/teachers"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Clara Souza\",\"email\":\"clara@example.test\"}"))
                .andExpect(status().isCreated())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.teacher.id").value(TEACHER.toString()))
                .andExpect(jsonPath("$.teacher.active").value(true))
                .andExpect(jsonPath("$.accessCode").value(ACCESS_CODE))
                .andExpect(jsonPath("$.validUntil").exists())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        ArgumentCaptor<RegisterTeacherUseCase.Command> command =
                ArgumentCaptor.forClass(RegisterTeacherUseCase.Command.class);
        verify(registerTeacher).run(any(Actor.class), command.capture());
        assertThat(command.getValue().name()).isEqualTo("Clara Souza");
        assertThat(command.getValue().email()).isEqualTo("clara@example.test");
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void duplicateEmailNamesTheExistingTeacher() throws Exception {
        given(registerTeacher.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.DUPLICATE_EMAIL, Map.of("name", "Clara Souza")));

        mvc.perform(
                        asAdmin(post(BASE + "/teachers"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Another\",\"email\":\"clara@example.test\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.details.name").value("Clara Souza"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Já existe um cadastro com este e-mail: Clara Souza."));
    }

    @Test
    void validationErrorsComeInDetailsFields() throws Exception {
        given(registerTeacher.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of("field", "name", "code", "INVALID_NAME"),
                                                Map.of(
                                                        "field",
                                                        "email",
                                                        "code",
                                                        "INVALID_EMAIL")))));

        mvc.perform(
                        asAdmin(post(BASE + "/teachers"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Nome Valido\",\"email\":\"invalido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.details.fields.length()").value(2))
                .andExpect(jsonPath("$.details.fields[1].code").value("INVALID_EMAIL"));
    }

    @Test
    void getReturnsTeacherAndUnknownIdIs404() throws Exception {
        given(getTeacher.run(any(), any())).willReturn(view(true));
        mvc.perform(get(BASE + "/teachers/" + TEACHER).cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.email").value("clara@example.test"))
                .andExpect(jsonPath("$.accessCode").doesNotExist());

        given(getTeacher.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get(BASE + "/teachers/" + UUID.randomUUID()).cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void malformedIdentifierIsNotFoundOrInvalidNeverServerError() throws Exception {
        MvcResult result =
                mvc.perform(get(BASE + "/teachers/not-a-uuid").cookie(adminCookie(ADMIN_TOKEN)))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void patchUpdatesNameAndEmail() throws Exception {
        given(updateTeacher.run(any(), any())).willReturn(view(true));

        mvc.perform(
                        asAdmin(patch(BASE + "/teachers/" + TEACHER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Clara S.\",\"email\":\"fresh@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(followsContract());

        ArgumentCaptor<UpdateTeacherUseCase.Command> command =
                ArgumentCaptor.forClass(UpdateTeacherUseCase.Command.class);
        verify(updateTeacher).run(any(Actor.class), command.capture());
        assertThat(command.getValue().teacherId()).isEqualTo(TEACHER);
        assertThat(command.getValue().name()).isEqualTo("Clara S.");
        assertThat(command.getValue().email()).isEqualTo("fresh@example.test");
    }

    @Test
    void inactivationReturnsTheTeacherInactive() throws Exception {
        given(inactivateTeacher.run(any(), any())).willReturn(view(false));

        mvc.perform(asAdmin(post(BASE + "/teachers/" + TEACHER + "/inactivation")))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.accessCode").doesNotExist());
    }

    @Test
    void reactivationReturnsANewCodeOnce() throws Exception {
        given(reactivateTeacher.run(any(), any())).willReturn(withCode());

        mvc.perform(asAdmin(post(BASE + "/teachers/" + TEACHER + "/reactivation")))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.accessCode").value(ACCESS_CODE))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void theCodeNeverAppearsInLogsOrUrls() throws Exception {
        given(registerTeacher.run(any(), any())).willReturn(withCode());
        try (var log = br.com.felipeblaft.reports.support.LogCapture.start()) {
            MvcResult result =
                    mvc.perform(
                                    asAdmin(post(BASE + "/teachers"))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"name\":\"Clara Souza\",\"email\":\"clara@example.test\"}"))
                            .andReturn();
            assertThat(log.text()).doesNotContain(ACCESS_CODE);
            assertThat(result.getRequest().getRequestURL().toString()).doesNotContain(ACCESS_CODE);
            assertThat(result.getResponse().getHeaderNames())
                    .noneMatch(name -> result.getResponse().getHeader(name).contains(ACCESS_CODE));
        }
    }

    @Test
    void expiredCodeInstantIsSerializedAsDateTime() throws Exception {
        Instant validUntil = Instant.parse("2026-11-01T12:00:00Z");
        given(registerTeacher.run(any(), any()))
                .willReturn(new TeacherWithCode(view(true), new Secret(ACCESS_CODE), validUntil));

        mvc.perform(
                        asAdmin(post(BASE + "/teachers"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Clara Souza\",\"email\":\"clara@example.test\"}"))
                .andExpect(jsonPath("$.validUntil").value("2026-11-01T12:00:00Z"));
    }
}
