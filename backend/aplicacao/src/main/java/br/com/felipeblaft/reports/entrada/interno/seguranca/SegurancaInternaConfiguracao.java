package br.com.felipeblaft.reports.entrada.interno.seguranca;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
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
public class SegurancaInternaConfiguracao {

    private static final Logger LOG = LoggerFactory.getLogger(SegurancaInternaConfiguracao.class);

    @Bean
    @Order(1)
    SecurityFilterChain cadeiaSegurancaInterna(
            HttpSecurity http, PropriedadesAplicacao propriedades) throws Exception {
        PropriedadesAplicacao.CanalTelegram canal = propriedades.canalTelegram();
        avisarSeNaoHaToken(canal);
        http.securityMatcher("/interno/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(
                        cabecalhos ->
                                cabecalhos
                                        .contentTypeOptions(opcao -> {})
                                        .cacheControl(opcao -> {}))
                .authorizeHttpRequests(
                        regras -> regras.anyRequest().hasAuthority(TokenServicoFiltro.AUTORIDADE))
                .exceptionHandling(
                        excecoes ->
                                excecoes.authenticationEntryPoint(
                                                (requisicao, resposta, erro) ->
                                                        resposta.setStatus(
                                                                HttpStatus.UNAUTHORIZED.value()))
                                        .accessDeniedHandler(
                                                (requisicao, resposta, erro) ->
                                                        resposta.setStatus(
                                                                HttpStatus.UNAUTHORIZED.value())))
                .addFilterBefore(new TokenServicoFiltro(canal), AuthorizationFilter.class);
        return http.build();
    }

    /** Aviso na subida: sem token a área interna recusa tudo. Nunca imprime valor algum. */
    static void avisarSeNaoHaToken(PropriedadesAplicacao.CanalTelegram canal) {
        if (vazio(canal.tokenAtual()) && vazio(canal.tokenProximo())) {
            LOG.warn(
                    "canal-telegram-sem-token-de-servico: a area /interno/** recusa todas as"
                            + " requisicoes ate que CANAL_TELEGRAM_TOKEN_ATUAL seja definido");
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
