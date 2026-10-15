package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.inbound.web.ErrorResponse;
import br.com.felipeblaft.reports.usecase.access.ValidateSessionWebUseCase;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da Web (R-12): sem sessão HTTP; autenticação pelo cookie {@code sessao} validado no
 * núcleo; CSRF com cookie {@code XSRF-TOKEN} e cabeçalho {@code X-CSRF-TOKEN}. O webhook do
 * Telegram fica fora de CSRF e de sessão (autenticado pelo segredo do cabeçalho). Só o Caddy expõe
 * a aplicação; os endpoints do Actuator ficam na rede interna.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    public static final String HEADER_CSRF = "X-CSRF-TOKEN";
    public static final String COOKIE_CSRF = "XSRF-TOKEN";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    @Order(2)
    SecurityFilterChain securityChain(
            HttpSecurity http,
            ValidateSessionWebUseCase validateSession,
            SessionCookie cookieSession,
            MessageCatalog catalog,
            ApplicationProperties properties)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setHeaderName(HEADER_CSRF);
        csrfRepository.setCookieName(COOKIE_CSRF);
        csrfRepository.setCookieCustomizer(
                cookie ->
                        cookie.path("/")
                                .sameSite("Strict")
                                .secure(properties.session().cookieSecure()));
        // Sem o atributo diferido o token é carregado em toda requisição: o SPA sempre recebe o
        // cookie.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http.csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfRepository)
                                        .csrfTokenRequestHandler(csrfHandler)
                                        .ignoringRequestMatchers("/telegram/webhook"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(anonymous -> anonymous.principal("anonymous"))
                .headers(
                        headers ->
                                headers.contentTypeOptions(option -> {})
                                        .frameOptions(option -> option.sameOrigin())
                                        .cacheControl(option -> {}))
                .authorizeHttpRequests(
                        rules ->
                                rules.requestMatchers(HttpMethod.POST, "/api/v1/session")
                                        .permitAll()
                                        .requestMatchers("/telegram/webhook", "/error")
                                        .permitAll()
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, error) -> {
                                                    ErrorCode code =
                                                            request.getAttribute(
                                                                                    SessionCookieFilter
                                                                                            .CODE_ATTRIBUTE)
                                                                            instanceof ErrorCode c
                                                                    ? c
                                                                    : ErrorCode.NOT_AUTHENTICATED;
                                                    if (code == ErrorCode.SESSION_EXPIRED) {
                                                        response.addHeader(
                                                                SessionCookie.header(),
                                                                cookieSession.remove());
                                                    }
                                                    write(response, 401, code, catalog);
                                                })
                                        .accessDeniedHandler(
                                                (request, response, error) ->
                                                        write(
                                                                response,
                                                                403,
                                                                ErrorCode.INVALID_DATA,
                                                                catalog)))
                .addFilterBefore(
                        new SessionCookieFilter(validateSession, cookieSession),
                        AuthorizationFilter.class);
        return http.build();
    }

    /** Não há usuários e senhas: a autenticação é só pela sessão do núcleo. */
    @Bean
    UserDetailsService withoutUsers() {
        return name -> {
            throw new UsernameNotFoundException("sem usuários");
        };
    }

    private static void write(
            HttpServletResponse response, int status, ErrorCode code, MessageCatalog catalog)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body =
                new ErrorResponse(code.name(), catalog.text(code, Map.of()), null, null);
        response.getWriter().write(JSON.writeValueAsString(body));
    }
}
