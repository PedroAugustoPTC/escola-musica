package br.com.felipeblaft.relatorios.entrada.web.seguranca;

import br.com.felipeblaft.relatorios.casodeuso.acesso.ValidarSessaoWebCasoDeUso;
import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.entrada.web.ErroResposta;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class SegurancaConfiguracao {

    public static final String CABECALHO_CSRF = "X-CSRF-TOKEN";
    public static final String COOKIE_CSRF = "XSRF-TOKEN";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    SecurityFilterChain cadeiaSeguranca(
            HttpSecurity http,
            ValidarSessaoWebCasoDeUso validarSessao,
            CookieSessao cookieSessao,
            CatalogoMensagens catalogo,
            PropriedadesAplicacao propriedades)
            throws Exception {
        CookieCsrfTokenRepository repositorioCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repositorioCsrf.setHeaderName(CABECALHO_CSRF);
        repositorioCsrf.setCookieName(COOKIE_CSRF);
        repositorioCsrf.setCookieCustomizer(
                cookie ->
                        cookie.path("/")
                                .sameSite("Strict")
                                .secure(propriedades.sessao().cookieSeguro()));
        // Sem o atributo diferido o token é carregado em toda requisição: o SPA sempre recebe o
        // cookie.
        CsrfTokenRequestAttributeHandler tratadorCsrf = new CsrfTokenRequestAttributeHandler();
        tratadorCsrf.setCsrfRequestAttributeName(null);

        http.csrf(
                        csrf ->
                                csrf.csrfTokenRepository(repositorioCsrf)
                                        .csrfTokenRequestHandler(tratadorCsrf)
                                        .ignoringRequestMatchers("/telegram/webhook"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(anonimo -> anonimo.principal("anonimo"))
                .headers(
                        cabecalhos ->
                                cabecalhos
                                        .contentTypeOptions(opcao -> {})
                                        .frameOptions(opcao -> opcao.sameOrigin())
                                        .cacheControl(opcao -> {}))
                .authorizeHttpRequests(
                        regras ->
                                regras.requestMatchers(HttpMethod.POST, "/api/v1/sessao")
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
                        excecoes ->
                                excecoes.authenticationEntryPoint(
                                                (requisicao, resposta, erro) -> {
                                                    CodigoErro codigo =
                                                            requisicao.getAttribute(
                                                                                    SessaoCookieFiltro
                                                                                            .ATRIBUTO_CODIGO)
                                                                            instanceof CodigoErro c
                                                                    ? c
                                                                    : CodigoErro.NAO_AUTENTICADO;
                                                    if (codigo == CodigoErro.SESSAO_EXPIRADA) {
                                                        resposta.addHeader(
                                                                CookieSessao.cabecalho(),
                                                                cookieSessao.remover());
                                                    }
                                                    escrever(resposta, 401, codigo, catalogo);
                                                })
                                        .accessDeniedHandler(
                                                (requisicao, resposta, erro) ->
                                                        escrever(
                                                                resposta,
                                                                403,
                                                                CodigoErro.DADOS_INVALIDOS,
                                                                catalogo)))
                .addFilterBefore(
                        new SessaoCookieFiltro(validarSessao, cookieSessao),
                        AuthorizationFilter.class);
        return http.build();
    }

    /** Não há usuários e senhas: a autenticação é só pela sessão do núcleo. */
    @Bean
    UserDetailsService semUsuarios() {
        return nome -> {
            throw new UsernameNotFoundException("sem usuários");
        };
    }

    private static void escrever(
            HttpServletResponse resposta, int status, CodigoErro codigo, CatalogoMensagens catalogo)
            throws IOException {
        resposta.setStatus(status);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErroResposta corpo =
                new ErroResposta(codigo.name(), catalogo.texto(codigo, Map.of()), null, null);
        resposta.getWriter().write(JSON.writeValueAsString(corpo));
    }
}
