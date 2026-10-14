package br.com.felipeblaft.reports.inbound.web.security;

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
public class SessionCookieFilter extends OncePerRequestFilter {

    public static final String CODE_ATTRIBUTE = SessionCookieFilter.class.getName() + ".code";

    private final ValidateSessionWebUseCase validateSession;
    private final SessionCookie cookie;

    public SessionCookieFilter(ValidateSessionWebUseCase validateSession, SessionCookie cookie) {
        this.validateSession = validateSession;
        this.cookie = cookie;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain cadeia)
            throws ServletException, IOException {
        String token = cookie.read(request);
        if (token != null && !token.isBlank()) {
            try {
                ValidateSessionWebUseCase.Result result = validateSession.run(token);
                AuthenticatedTeacher principal =
                        new AuthenticatedTeacher(result.teacher(), result.session().expiresAt());
                var authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("TEACHER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (DomainException error) {
                request.setAttribute(CODE_ATTRIBUTE, ErrorCode.SESSION_EXPIRED);
            }
        }
        // o contexto é limpo pelo SecurityContextHolderFilter ao fim da requisição; limpar aqui
        // esconderia a autenticação do ExceptionTranslationFilter (401 viraria 403)
        cadeia.doFilter(request, response);
    }
}
