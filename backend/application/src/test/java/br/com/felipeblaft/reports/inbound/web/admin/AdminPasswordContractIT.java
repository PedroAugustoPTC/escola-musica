package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorSessionResult;
import br.com.felipeblaft.reports.usecase.administrator.ChangeProvisionalPasswordUseCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Contrato de {@code PUT /admin/password} (FR-034a): troca da senha provisória e cookie novo. */
class AdminPasswordContractIT extends AdminWebBase {

    private static final String NEW_PASSWORD = "senha-definitiva-bem-longa";
    private static final String BODY = "{\"newPassword\":\"" + NEW_PASSWORD + "\"}";

    @Test
    void changeReturnsTheNewSessionWithANewCookie() throws Exception {
        authenticate("provisional-token", true);
        given(changePassword.run(any(), any()))
                .willReturn(
                        new AdministratorSessionResult(
                                administratorId,
                                "Administrador de Teste",
                                new Secret("token-depois-da-troca"),
                                sessionExpiry,
                                false));

        try (LogCapture log = LogCapture.start()) {
            MvcResult result =
                    mvc.perform(
                                    put(BASE + "/password")
                                            .cookie(adminCookie("provisional-token"))
                                            .with(
                                                    org.springframework.security.test.web.servlet
                                                            .request
                                                            .SecurityMockMvcRequestPostProcessors
                                                            .csrf()
                                                            .asHeader())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(BODY))
                            .andExpect(status().isOk())
                            .andExpect(followsContract())
                            .andExpect(jsonPath("$.mustChangePassword").value(false))
                            .andExpect(header().string("Cache-Control", containsString("no-store")))
                            .andReturn();

            assertThat(result.getResponse().getHeader("Set-Cookie"))
                    .startsWith("admin_session=token-depois-da-troca;")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Strict")
                    .contains("Path=/api/v1/admin");
            assertThat(result.getResponse().getContentAsString()).doesNotContain(NEW_PASSWORD);
            assertThat(log.text()).doesNotContain(NEW_PASSWORD);
        }

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<ChangeProvisionalPasswordUseCase.Command> command =
                ArgumentCaptor.forClass(ChangeProvisionalPasswordUseCase.Command.class);
        verify(changePassword).run(actor.capture(), command.capture());
        assertThat(actor.getValue().passwordChangeRequired()).isTrue();
        assertThat(command.getValue().newPassword()).isEqualTo(new Secret(NEW_PASSWORD));
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void passwordRuleViolationsComeAsFields() throws Exception {
        given(changePassword.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "newPassword",
                                                        "code",
                                                        "PASSWORD_SAME_AS_PROVISIONAL")))));

        MvcResult result =
                mvc.perform(
                                asAdmin(put(BASE + "/password"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BODY))
                        .andExpect(status().isBadRequest())
                        .andExpect(followsContract())
                        .andExpect(
                                jsonPath("$.details.fields[0].code")
                                        .value("PASSWORD_SAME_AS_PROVISIONAL"))
                        .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void accountWithoutProvisionalPasswordIs409() throws Exception {
        given(changePassword.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.PASSWORD_CHANGE_NOT_REQUIRED));

        mvc.perform(
                        asAdmin(put(BASE + "/password"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_NOT_REQUIRED"));
    }

    @Test
    void withoutSessionIs401AndWithoutTheNewPasswordIs400() throws Exception {
        mvc.perform(
                        put(BASE + "/password")
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.csrf()
                                                .asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract());

        mvc.perform(
                        asAdmin(put(BASE + "/password"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
