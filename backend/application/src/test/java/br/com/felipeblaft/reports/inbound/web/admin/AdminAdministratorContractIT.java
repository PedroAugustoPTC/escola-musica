package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.usecase.administrator.ActivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorView;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorView.ActivationState;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorWithCode;
import br.com.felipeblaft.reports.usecase.administrator.CreateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ListAdministratorsUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato de {@code /admin/administrators} e de {@code POST /admin/activation} (FR-032, FR-033,
 * FR-034): o código de ativação só aparece na resposta que o gera; a ativação é pública.
 */
class AdminAdministratorContractIT extends AdminWebBase {

    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final String ACTIVATION_CODE = "K7QM2XPA9TBD";
    private static final String NEW_PASSWORD = "uma-senha-nova-bem-longa";

    private AdministratorView view(ActivationState state, boolean active) {
        return new AdministratorView(
                OTHER,
                "Maria Diretora",
                "maria@example.test",
                active,
                state,
                state == ActivationState.ACTIVATED ? Instant.parse("2026-10-01T10:00:00Z") : null);
    }

    private AdministratorWithCode withCode() {
        return new AdministratorWithCode(
                view(ActivationState.PENDING, true),
                new Secret(ACTIVATION_CODE),
                clock.now().plusSeconds(7 * 86400));
    }

    @Test
    void listShowsActivationStateWithoutAnyCode() throws Exception {
        given(listAdministrators.run(any(), any()))
                .willReturn(
                        new PageResult<>(
                                List.of(
                                        view(ActivationState.ACTIVATED, true),
                                        view(ActivationState.PENDING, true)),
                                0,
                                20,
                                2));

        MvcResult result =
                mvc.perform(
                                get(BASE + "/administrators")
                                        .cookie(adminCookie(ADMIN_TOKEN))
                                        .param("active", "true"))
                        .andExpect(status().isOk())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.items[0].activationState").value("ACTIVATED"))
                        .andExpect(jsonPath("$.items[0].activatedAt").exists())
                        .andExpect(jsonPath("$.items[1].activationState").value("PENDING"))
                        .andExpect(jsonPath("$.items[1].activatedAt").doesNotExist())
                        .andExpect(header().string("Cache-Control", containsString("no-store")))
                        .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("activationCode")
                .doesNotContain(ACTIVATION_CODE)
                .doesNotContain("password");

        ArgumentCaptor<ListAdministratorsUseCase.Command> command =
                ArgumentCaptor.forClass(ListAdministratorsUseCase.Command.class);
        verify(listAdministrators).run(any(), command.capture());
        assertThat(command.getValue().active()).isTrue();
        assertThat(command.getValue().search()).isNull();
        assertThat(command.getValue().page().size()).isEqualTo(20);
    }

    @Test
    void createReturnsTheActivationCodeOnlyInThisResponse() throws Exception {
        given(createAdministrator.run(any(), any())).willReturn(withCode());

        try (LogCapture log = LogCapture.start()) {
            MvcResult result =
                    mvc.perform(
                                    asAdmin(post(BASE + "/administrators"))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"name\":\"Maria Diretora\",\"email\":\"maria@example.test\"}"))
                            .andExpect(status().isCreated())
                            .andExpect(followsContract())
                            .andExpect(jsonPath("$.activationCode").value(ACTIVATION_CODE))
                            .andExpect(jsonPath("$.administrator.activationState").value("PENDING"))
                            .andExpect(jsonPath("$.validUntil").exists())
                            .andExpect(header().string("Cache-Control", containsString("no-store")))
                            .andReturn();
            assertThat(log.text()).doesNotContain(ACTIVATION_CODE);
            assertThat(result.getResponse().getHeaderNames())
                    .noneMatch(name -> result.getResponse().getHeader(name).contains(ACTIVATION_CODE));
        }

        ArgumentCaptor<CreateAdministratorUseCase.Command> command =
                ArgumentCaptor.forClass(CreateAdministratorUseCase.Command.class);
        verify(createAdministrator).run(any(), command.capture());
        assertThat(command.getValue().name()).isEqualTo("Maria Diretora");
        assertThat(command.getValue().email()).isEqualTo("maria@example.test");
    }

    @Test
    void duplicateEmailNamesTheExistingAccount() throws Exception {
        given(createAdministrator.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.DUPLICATE_EMAIL, Map.of("name", "Maria Diretora")));

        mvc.perform(
                        asAdmin(post(BASE + "/administrators"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Outra\",\"email\":\"maria@example.test\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.details.name").value("Maria Diretora"));
    }

    @Test
    void inactivationReturnsTheAdministratorAndTheLastOneIsProtected() throws Exception {
        given(inactivateAdministrator.run(any(), any()))
                .willReturn(view(ActivationState.ACTIVATED, false));

        mvc.perform(asAdmin(post(BASE + "/administrators/" + OTHER + "/inactivation")))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.active").value(false));

        given(inactivateAdministrator.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.LAST_ADMINISTRATOR));
        mvc.perform(asAdmin(post(BASE + "/administrators/" + OTHER + "/inactivation")))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("LAST_ADMINISTRATOR"))
                .andExpect(
                        jsonPath("$.message")
                                .value("A escola precisa ter pelo menos um administrador ativo."));
    }

    @Test
    void reactivationReturnsANewActivationCode() throws Exception {
        given(reactivateAdministrator.run(any(), any())).willReturn(withCode());

        mvc.perform(asAdmin(post(BASE + "/administrators/" + OTHER + "/reactivation")))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.activationCode").value(ACTIVATION_CODE));
    }

    @Test
    void activationIsPublicNeedsNoSessionAndReturns204() throws Exception {
        MvcResult result =
                mvc.perform(
                                post(BASE + "/activation")
                                        .with(csrf().asHeader())
                                        .with(
                                                r -> {
                                                    r.setRemoteAddr("200.1.1.1");
                                                    return r;
                                                })
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(activationBody()))
                        .andExpect(status().isNoContent())
                        .andExpect(followsContract())
                        .andReturn();
        // não abre sessão
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(value -> value.startsWith("admin_session="));

        ArgumentCaptor<ActivateAdministratorUseCase.Command> command =
                ArgumentCaptor.forClass(ActivateAdministratorUseCase.Command.class);
        verify(activate).run(command.capture());
        assertThat(command.getValue().email()).isEqualTo("maria@example.test");
        assertThat(command.getValue().activationCode()).isEqualTo(new Secret(ACTIVATION_CODE));
        assertThat(command.getValue().newPassword()).isEqualTo(new Secret(NEW_PASSWORD));
        assertThat(command.getValue().origin()).isEqualTo("200.1.1.1");
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void activationNeedsTheCsrfToken() throws Exception {
        mvc.perform(
                        post(BASE + "/activation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activationBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidAndBlockedActivationAreTheContractedAnswers() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException(ErrorCode.INVALID_ACTIVATION))
                .when(activate)
                .run(any());
        mvc.perform(
                        post(BASE + "/activation")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activationBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVATION"));

        org.mockito.Mockito.doThrow(
                        new DomainException(
                                ErrorCode.TEMPORARILY_BLOCKED, Map.of("remainingSeconds", 600L)))
                .when(activate)
                .run(any());
        mvc.perform(
                        post(BASE + "/activation")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(activationBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(followsContract())
                .andExpect(header().string("Retry-After", "600"));
    }

    @Test
    void shortPasswordComesAsAFieldViolationWithoutEchoingTheValue() throws Exception {
        org.mockito.Mockito.doThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "newPassword",
                                                        "code",
                                                        "PASSWORD_LENGTH")))))
                .when(activate)
                .run(any());

        MvcResult result =
                mvc.perform(
                                post(BASE + "/activation")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(activationBody()))
                        .andExpect(status().isBadRequest())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.details.fields[0].code").value("PASSWORD_LENGTH"))
                        .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(ACTIVATION_CODE);
    }

    @Test
    void secretsNeverReachTheLog() throws Exception {
        try (LogCapture log = LogCapture.start()) {
            mvc.perform(
                    post(BASE + "/activation")
                            .with(csrf().asHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(activationBody()));
            assertThat(log.text()).doesNotContain(NEW_PASSWORD).doesNotContain(ACTIVATION_CODE);
        }
    }

    private static String activationBody() {
        return "{\"email\":\"maria@example.test\",\"activationCode\":\""
                + ACTIVATION_CODE
                + "\",\"newPassword\":\""
                + NEW_PASSWORD
                + "\"}";
    }
}
