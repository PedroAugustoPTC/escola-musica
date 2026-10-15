package br.com.felipeblaft.reports.inbound.internal.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Segunda cadeia de segurança, só para {@code /interno/**} (R-09, FR-024 a FR-027): sem sessão, sem
 * cookie de portal, sem CSRF (não há navegador), autenticada somente pelo token de serviço. Falha
 * de autenticação responde {@code 401} <strong>sem corpo</strong>. A cadeia do portal não muda além
 * da ordem ({@code @Order(2)}); o token de serviço não autentica nada nela.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(InternalSecurityConfig.class);

    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityChain(HttpSecurity http, ApplicationProperties properties)
            throws Exception {
        ApplicationProperties.TelegramChannel channel = properties.telegramChannel();
        warnIfThereIsNoToken(channel);
        http.securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(
                        headers ->
                                headers.contentTypeOptions(option -> {}).cacheControl(option -> {}))
                .authorizeHttpRequests(
                        rules -> rules.anyRequest().hasAuthority(ServiceTokenFilter.AUTHORITY))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, error) ->
                                                        response.setStatus(
                                                                HttpStatus.UNAUTHORIZED.value()))
                                        .accessDeniedHandler(
                                                (request, response, error) ->
                                                        response.setStatus(
                                                                HttpStatus.UNAUTHORIZED.value())))
                .addFilterBefore(new ServiceTokenFilter(channel), AuthorizationFilter.class);
        return http.build();
    }

    /** Aviso na subida: sem token a área interna recusa tudo. Nunca imprime valor algum. */
    static void warnIfThereIsNoToken(ApplicationProperties.TelegramChannel channel) {
        if (empty(channel.currentToken()) && empty(channel.nextToken())) {
            LOG.warn(
                    "telegram-channel-without-service-token: a area /internal/** recusa todas as"
                            + " requisicoes ate que TELEGRAM_CHANNEL_CURRENT_TOKEN seja definido");
        }
    }

    private static boolean empty(String value) {
        return value == null || value.isBlank();
    }
}
