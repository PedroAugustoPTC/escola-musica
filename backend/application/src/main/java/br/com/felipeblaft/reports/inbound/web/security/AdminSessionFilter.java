package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.inbound.web.RequestCorrelation;
import br.com.felipeblaft.reports.usecase.administrator.ValidateAdministratorSessionUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê o cookie {@code admin_session} das rotas administrativas e valida a sessão no núcleo em
 * <b>toda</b> requisição, sem cache de autorização (FR-047): a revogação vale na requisição
 * seguinte. Também carimba cada requisição com um identificador de correlação (autoria, FR-040).
 *
 * <p>Sessão inválida não autentica; o ponto de entrada responde 401 com o código registrado em
 * {@link #CODE_ATTRIBUTE}. Quando a conta ainda está com a senha provisória, o filtro é uma segunda
 * barreira, antes de qualquer caso de uso: só a leitura da sessão, "Sair" e a troca da senha passam
 * (a decisão definitiva continua no núcleo, pela matriz de permissões).
 */
public class AdminSessionFilter extends OncePerRequestFilter {

    public static final String CODE_ATTRIBUTE = AdminSessionFilter.class.getName() + ".code";

    /** Mesmo atributo das rotas do professor ({@link RequestCorrelation}). */
    public static final String CORRELATION_ATTRIBUTE = RequestCorrelation.ATTRIBUTE;

    private static final String PREFIX = "/api/v1/admin/";
    private static final String SESSION = PREFIX + "session";
    private static final String ACTIVATION = PREFIX + "activation";
    private static final String PASSWORD = PREFIX + "password";
    private static final Set<ErrorCode> SESSION_CODES =
            Set.of(ErrorCode.NOT_AUTHENTICATED, ErrorCode.SESSION_EXPIRED, ErrorCode.ACCESS_ENDED);

    private final ValidateAdministratorSessionUseCase validateSession;
    private final AdminSessionCookie cookie;
    private final MessageCatalog catalog;

    public AdminSessionFilter(
            ValidateAdministratorSessionUseCase validateSession,
            AdminSessionCookie cookie,
            MessageCatalog catalog) {
        this.validateSession = validateSession;
        this.cookie = cookie;
        this.catalog = catalog;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RequestCorrelation.assign(request);
        if (isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }
        String token = cookie.read(request);
        if (token != null) {
            try {
                var result = validateSession.run(new Secret(token));
                var principal =
                        new AuthenticatedAdministrator(
                                result.actor(), result.expiresAt(), new Secret(token));
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ADMINISTRATOR"))));
                if (principal.actor().passwordChangeRequired()
                        && !allowedWhileRestricted(request)) {
                    SecurityResponses.write(
                            response, ErrorCode.PASSWORD_CHANGE_REQUIRED, 403, catalog);
                    return;
                }
            } catch (DomainException error) {
                request.setAttribute(
                        CODE_ATTRIBUTE,
                        SESSION_CODES.contains(error.code())
                                ? error.code()
                                : ErrorCode.SESSION_EXPIRED);
            }
        }
        chain.doFilter(request, response);
    }

    /** Rotas sem credencial: entrar e ativar a conta. */
    private static boolean isPublic(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && (SESSION.equals(request.getRequestURI())
                        || ACTIVATION.equals(request.getRequestURI()));
    }

    /** Senha provisória: ler a sessão, sair e trocar a senha (FR-034a). */
    private static boolean allowedWhileRestricted(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        return (SESSION.equals(uri)
                        && (HttpMethod.GET.matches(method) || HttpMethod.DELETE.matches(method)))
                || (PASSWORD.equals(uri) && HttpMethod.PUT.matches(method));
    }
}
