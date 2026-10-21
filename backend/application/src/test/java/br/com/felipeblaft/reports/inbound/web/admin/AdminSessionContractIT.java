package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorSessionResult;
import br.com.felipeblaft.reports.usecase.administrator.GetAdministratorSessionUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignInAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignOutAdministratorUseCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato de {@code POST/GET/DELETE /admin/session} (FR-004, FR-005, FR-006, FR-046) contra {@code
 * api-administration.openapi.yaml}: status, corpo, {@code details.fields}, cabeçalhos, cookie e
 * erros. Os casos de uso são dublês: a regra de negócio não é exercida aqui.
 */
class AdminSessionContractIT extends AdminWebBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SIGN_IN_BODY =
            "{\"email\":\"administrator.test@example.test\",\"password\":\"long-test-password-1\"}";
    private static final String INTERNAL_DETAIL = "detalhe-interno-secreto-xyz";

    private AdministratorSessionResult opened(String token, boolean mustChange) {
        return new AdministratorSessionResult(
                administratorId,
                "Administrador de Teste",
                new Secret(token),
                sessionExpiry,
                mustChange);
    }

    private ResultActions signInRequest(String remote, String forwarded) throws Exception {
        var request =
                post(BASE + "/session")
                        .with(csrf().asHeader())
                        .with(
                                r -> {
                                    r.setRemoteAddr(remote);
                                    return r;
                                })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGN_IN_BODY);
        if (forwarded != null) {
            request.header("X-Forwarded-For", forwarded);
        }
        return mvc.perform(request);
    }

    @Test
    void signInReturnsSessionAndSetsRestrictedCookie() throws Exception {
        given(signIn.run(any())).willReturn(opened("novo-token-1", false));

        MvcResult result =
                signInRequest("200.1.1.1", null)
                        .andExpect(status().isOk())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.administrator.id").value(administratorId.toString()))
                        .andExpect(jsonPath("$.administrator.name").value("Administrador de Teste"))
                        .andExpect(jsonPath("$.expiresAt").exists())
                        .andExpect(jsonPath("$.mustChangePassword").value(false))
                        .andExpect(header().string("Cache-Control", containsString("no-store")))
                        .andReturn();

        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie)
                .startsWith("admin_session=novo-token-1;")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/admin")
                .doesNotContain("Max-Age")
                .doesNotContain("Expires");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("novo-token-1")
                .doesNotContain("long-test-password-1");

        ArgumentCaptor<SignInAdministratorUseCase.Command> command =
                ArgumentCaptor.forClass(SignInAdministratorUseCase.Command.class);
        verify(signIn).run(command.capture());
        assertThat(command.getValue().email()).isEqualTo("administrator.test@example.test");
        assertThat(command.getValue().password()).isEqualTo(new Secret("long-test-password-1"));
        assertThat(command.getValue().origin()).isEqualTo("200.1.1.1");
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void sessionIdentifierComesFromTheServerNeverFromTheClient() throws Exception {
        given(signIn.run(any())).willReturn(opened("emitido-pelo-servidor", false));

        MvcResult result =
                mvc.perform(
                                post(BASE + "/session")
                                        .with(csrf().asHeader())
                                        .cookie(adminCookie("fixado-pelo-cliente"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(SIGN_IN_BODY))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("admin_session=emitido-pelo-servidor")
                .doesNotContain("fixado-pelo-cliente");
    }

    @Test
    void everySignInGetsANewIdentifier() throws Exception {
        given(signIn.run(any()))
                .willReturn(opened("id-a", false))
                .willReturn(opened("id-b", false));

        String first =
                signInRequest("200.1.1.1", null).andReturn().getResponse().getHeader("Set-Cookie");
        String second =
                signInRequest("200.1.1.1", null).andReturn().getResponse().getHeader("Set-Cookie");

        assertThat(first).contains("admin_session=id-a");
        assertThat(second).contains("admin_session=id-b");
    }

    @Test
    void forgedForwardedHeaderFromOutsideTheProxyNetworkDoesNotChangeTheOrigin() throws Exception {
        given(signIn.run(any())).willReturn(opened("t", false));

        signInRequest("200.1.1.1", "9.9.9.9").andExpect(status().isOk());

        ArgumentCaptor<SignInAdministratorUseCase.Command> command =
                ArgumentCaptor.forClass(SignInAdministratorUseCase.Command.class);
        verify(signIn).run(command.capture());
        assertThat(command.getValue().origin()).isEqualTo("200.1.1.1");
    }

    @Test
    void failuresOfTheThreeKindsLookIdentical() throws Exception {
        given(signIn.run(any())).willThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS));

        MvcResult first =
                signInRequest("200.1.1.1", null)
                        .andExpect(status().isUnauthorized())
                        .andExpect(followsContract())
                        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                        .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos."))
                        .andExpect(header().doesNotExist("Set-Cookie"))
                        .andReturn();
        MvcResult second = signInRequest("200.1.1.2", null).andReturn();

        assertThat(second.getResponse().getContentAsByteArray())
                .isEqualTo(first.getResponse().getContentAsByteArray());
        assertThat(second.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
    }

    @Test
    void lockReturns429WithRetryAfter() throws Exception {
        given(signIn.run(any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.TEMPORARILY_BLOCKED, Map.of("remainingSeconds", 900L)));

        signInRequest("200.1.1.1", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(followsContract())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.code").value("TEMPORARILY_BLOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900))
                .andExpect(jsonPath("$.message").value(containsString("15 minuto(s)")));
    }

    @Test
    void validationFailureCarriesFieldsAndNeverRepeatsTheValue() throws Exception {
        given(signIn.run(any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "email",
                                                        "code",
                                                        "INVALID_EMAIL")))));

        signInRequest("200.1.1.1", null)
                .andExpect(status().isBadRequest())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields[0].field").value("email"))
                .andExpect(jsonPath("$.details.fields[0].code").value("INVALID_EMAIL"))
                .andExpect(
                        content().string(not(containsString("administrator.test@example.test"))));
    }

    @Test
    void currentSessionReturnsTheFixedExpiry() throws Exception {
        given(getSession.run(any(), any()))
                .willReturn(
                        new GetAdministratorSessionUseCase.Result(
                                administratorId, "Administrador de Teste", sessionExpiry, true));

        mvc.perform(get(BASE + "/session").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.administrator.id").value(administratorId.toString()))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().doesNotExist("Set-Cookie"));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<GetAdministratorSessionUseCase.Command> command =
                ArgumentCaptor.forClass(GetAdministratorSessionUseCase.Command.class);
        verify(getSession).run(actor.capture(), command.capture());
        assertThat(actor.getValue().id()).isEqualTo(administratorId);
        assertThat(command.getValue().sessionToken()).isEqualTo(new Secret(ADMIN_TOKEN));
    }

    @Test
    void withoutCredentialTheAnswerIs401() throws Exception {
        mvc.perform(get(BASE + "/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    void signOutEndsOnlyThisSessionAndRemovesTheCookie() throws Exception {
        MvcResult result =
                mvc.perform(asAdmin(delete(BASE + "/session")))
                        .andExpect(status().isNoContent())
                        .andExpect(followsContract())
                        .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .startsWith("admin_session=;")
                .contains("Max-Age=0")
                .contains("Path=/api/v1/admin")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
        ArgumentCaptor<SignOutAdministratorUseCase.Command> command =
                ArgumentCaptor.forClass(SignOutAdministratorUseCase.Command.class);
        verify(signOut).run(any(Actor.class), command.capture());
        assertThat(command.getValue().sessionToken()).isEqualTo(new Secret(ADMIN_TOKEN));
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void internalFailureReturnsGenericTextWithReferenceAndNoTechnicalDetail() throws Exception {
        given(getSession.run(any(), any())).willThrow(new IllegalStateException(INTERNAL_DETAIL));

        try (LogCapture log = LogCapture.start()) {
            MvcResult result =
                    mvc.perform(get(BASE + "/session").cookie(adminCookie(ADMIN_TOKEN)))
                            .andExpect(status().isInternalServerError())
                            .andReturn();

            String body = result.getResponse().getContentAsString();
            JsonNode json = JSON.readTree(body);
            String reference = json.path("reference").asString();
            assertThat(json.path("code").asString()).isEqualTo("INTERNAL_ERROR");
            assertThat(reference).matches("[A-Z0-9]{8}");
            assertThat(json.path("message").asString())
                    .isEqualTo(
                            "Algo deu errado. Informe o código de referência "
                                    + reference
                                    + " ao suporte.");
            assertThat(body)
                    .doesNotContain(INTERNAL_DETAIL, "IllegalStateException", "br.com.felipeblaft");
            // o suporte consegue achar a falha pelo código de referência
            assertThat(log.text()).contains(reference).doesNotContain(INTERNAL_DETAIL);
        }
    }

    @Test
    void partialFailureReturnsOperationNotSaved() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException(ErrorCode.OPERATION_NOT_SAVED))
                .when(signOut)
                .run(any(), any());

        mvc.perform(asAdmin(delete(BASE + "/session")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_SAVED"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Não foi possível concluir. Nada foi gravado; tente de novo."))
                .andExpect(jsonPath("$.reference").doesNotExist());
    }

    @Test
    void noResponsePropertyIsCalledCpf() throws Exception {
        given(signIn.run(any())).willReturn(opened("t", false));
        String body =
                signInRequest("200.1.1.1", null).andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("cpf");
    }

    private static org.springframework.test.web.servlet.result.ContentResultMatchers content() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content();
    }
}
