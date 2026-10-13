package br.com.felipeblaft.reports.entrada.web.seguranca;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.usecase.access.ValidateSessionWebUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê o cookie {@code sessao} das requisições da API e valida a sessão no núcleo. Sessão inválida
 * não autentica; o ponto de entrada responde 401 com {@code SESSAO_EXPIRADA} (e remove o cookie).
 */
public class SessaoCookieFiltro extends OncePerRequestFilter {

    public static final String ATRIBUTO_CODIGO = SessaoCookieFiltro.class.getName() + ".codigo";

    private final ValidateSessionWebUseCase validarSessao;
    private final CookieSessao cookie;

    public SessaoCookieFiltro(ValidateSessionWebUseCase validarSessao, CookieSessao cookie) {
        this.validarSessao = validarSessao;
        this.cookie = cookie;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        return !requisicao.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String token = cookie.ler(requisicao);
        if (token != null && !token.isBlank()) {
            try {
                ValidateSessionWebUseCase.Result resultado = validarSessao.run(token);
                ProfessorAutenticado principal =
                        new ProfessorAutenticado(
                                resultado.teacher(), resultado.session().expiresAt());
                var autenticacao =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("PROFESSOR")));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            } catch (DomainException erro) {
                requisicao.setAttribute(ATRIBUTO_CODIGO, ErrorCode.SESSAO_EXPIRADA);
            }
        }
        // o contexto é limpo pelo SecurityContextHolderFilter ao fim da requisição; limpar aqui
        // esconderia a autenticação do ExceptionTranslationFilter (401 viraria 403)
        cadeia.doFilter(requisicao, resposta);
    }
}
