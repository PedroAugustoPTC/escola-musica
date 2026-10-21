package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.usecase.teacher.ReissueAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.teacher.TeacherView;
import br.com.felipeblaft.reports.usecase.teacher.TeacherWithCode;
import br.com.felipeblaft.reports.usecase.teacher.UnlinkTeacherTelegramUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato de {@code POST /admin/teachers/{id}/access-code} (opção de encerrar o vínculo; código só
 * na resposta) e {@code DELETE /admin/teachers/{id}/telegram-link} (FR-030, FR-031).
 */
class AdminTeacherAccessContractIT extends AdminWebBase {

    private static final UUID TEACHER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final String NEW_CODE = "N3WC0DE9";

    private TeacherWithCode reissued() {
        return new TeacherWithCode(
                new TeacherView(TEACHER, "Clara Souza", "clara@example.test", true, true),
                new Secret(NEW_CODE),
                clock.now().plusSeconds(7 * 86400));
    }

    @Test
    void reissueReturnsTheNewCodeOnlyInThisResponse() throws Exception {
        given(reissueCode.run(any(), any())).willReturn(reissued());

        try (LogCapture log = LogCapture.start()) {
            MvcResult result =
                    mvc.perform(
                                    asAdmin(post(BASE + "/teachers/" + TEACHER + "/access-code"))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"endTelegramLink\":true}"))
                            .andExpect(status().isOk())
                            .andExpect(followsContract())
                            .andExpect(jsonPath("$.accessCode").value(NEW_CODE))
                            .andExpect(jsonPath("$.teacher.telegramLinked").value(true))
                            .andExpect(header().string("Cache-Control", containsString("no-store")))
                            .andReturn();
            assertThat(log.text()).doesNotContain(NEW_CODE);
            assertThat(result.getResponse().getHeaderNames())
                    .noneMatch(name -> result.getResponse().getHeader(name).contains(NEW_CODE));
        }

        ArgumentCaptor<ReissueAccessCodeUseCase.Command> command =
                ArgumentCaptor.forClass(ReissueAccessCodeUseCase.Command.class);
        verify(reissueCode).run(any(), command.capture());
        assertThat(command.getValue().teacherId()).isEqualTo(TEACHER);
        assertThat(command.getValue().endTelegramLink()).isTrue();
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void reissueWithoutEndingTheLinkPassesFalse() throws Exception {
        given(reissueCode.run(any(), any())).willReturn(reissued());

        mvc.perform(
                        asAdmin(post(BASE + "/teachers/" + TEACHER + "/access-code"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"endTelegramLink\":false}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReissueAccessCodeUseCase.Command> command =
                ArgumentCaptor.forClass(ReissueAccessCodeUseCase.Command.class);
        verify(reissueCode).run(any(), command.capture());
        assertThat(command.getValue().endTelegramLink()).isFalse();
    }

    @Test
    void reissueWithoutTheRequiredOptionIs400() throws Exception {
        mvc.perform(
                        asAdmin(post(BASE + "/teachers/" + TEACHER + "/access-code"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reissueForAnInactiveTeacherIs409() throws Exception {
        given(reissueCode.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.TEACHER_INACTIVE));

        mvc.perform(
                        asAdmin(post(BASE + "/teachers/" + TEACHER + "/access-code"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"endTelegramLink\":false}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("TEACHER_INACTIVE"));
    }

    @Test
    void reissueForUnknownTeacherIs404() throws Exception {
        given(reissueCode.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(
                        asAdmin(post(BASE + "/teachers/" + UUID.randomUUID() + "/access-code"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"endTelegramLink\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
    }

    @Test
    void unlinkTelegramReturns204() throws Exception {
        mvc.perform(asAdmin(delete(BASE + "/teachers/" + TEACHER + "/telegram-link")))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());

        ArgumentCaptor<UnlinkTeacherTelegramUseCase.Command> command =
                ArgumentCaptor.forClass(UnlinkTeacherTelegramUseCase.Command.class);
        verify(unlinkTelegram).run(any(), command.capture());
        assertThat(command.getValue().teacherId()).isEqualTo(TEACHER);
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void unlinkForUnknownTeacherIs404() throws Exception {
        doThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(unlinkTelegram)
                .run(any(), any());

        mvc.perform(asAdmin(delete(BASE + "/teachers/" + TEACHER + "/telegram-link")))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
    }
}
