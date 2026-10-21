package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.usecase.administrator.ValidateAdministratorSessionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Cadeia de segurança da área administrativa (R-03, R-19, FR-003): só {@code /api/v1/admin/**},
 * cookie {@code admin_session} próprio e validado no núcleo a cada requisição, CSRF em toda
 * gravação (inclusive a entrada e a ativação, o que impede o <em>login CSRF</em>) e {@code
 * Cache-Control: no-store}. Entrar e ativar a conta são as únicas rotas sem credencial.
 *
 * <p>Sem credencial: 401. Com a credencial válida do professor e sem a do administrador: a resposta
 * de rota inexistente ({@link CrossCredentialResponder}). Fica antes da cadeia geral do professor.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminSecurityConfig {

    private static final String ADMIN_PATTERN = "/api/v1/admin/**";

    @Bean
    @Order(2)
    SecurityFilterChain adminSecurityChain(
            HttpSecurity http,
            ValidateAdministratorSessionUseCase validateSession,
            AdminSessionCookie cookie,
            CrossCredentialResponder crossCredential,
            MessageCatalog catalog,
            ApplicationProperties properties)
            throws Exception {
        SecurityConfig.applyCommon(http, properties);
        http.securityMatcher(ADMIN_PATTERN)
                .authorizeHttpRequests(
                        rules ->
                                rules.requestMatchers(HttpMethod.POST, "/api/v1/admin/session")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/v1/admin/activation")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, error) -> {
                                                    ErrorCode code =
                                                            request.getAttribute(
                                                                                    AdminSessionFilter
                                                                                            .CODE_ATTRIBUTE)
                                                                            instanceof ErrorCode c
                                                                    ? c
                                                                    : ErrorCode.NOT_AUTHENTICATED;
                                                    if (code == ErrorCode.NOT_AUTHENTICATED
                                                            && crossCredential
                                                                    .validTeacherCredential(
                                                                            request)) {
                                                        // credencial do professor numa rota
                                                        // administrativa: rota inexistente (FR-003)
                                                        crossCredential.writeNotFound(response);
                                                        return;
                                                    }
                                                    if (code != ErrorCode.NOT_AUTHENTICATED) {
                                                        response.addHeader(
                                                                AdminSessionCookie.header(),
                                                                cookie.remove());
                                                    }
                                                    SecurityResponses.write(
                                                            response, code, 401, catalog);
                                                })
                                        .accessDeniedHandler(
                                                (request, response, error) ->
                                                        SecurityResponses.write(
                                                                response,
                                                                ErrorCode.INVALID_DATA,
                                                                403,
                                                                catalog)))
                .addFilterBefore(
                        new AdminSessionFilter(validateSession, cookie, catalog),
                        AuthorizationFilter.class);
        return http.build();
    }
}
