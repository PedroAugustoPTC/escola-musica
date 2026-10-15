package br.com.felipeblaft.reports.inbound.internal.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
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
 * Autentica o orquestrador pelo cabeçalho {@code X-N8N-Service-Token} (R-09): comparação em tempo
 * constante com até dois tokens (atual e próximo, para rotação sem indisponibilidade). Sem nenhum
 * token configurado ninguém é autenticado (<em>fail-closed</em>). O token nunca é registrado em
 * log. Um cookie de sessão do portal é ignorado: só este cabeçalho autentica a área interna.
 */
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-N8N-Service-Token";
    public static final String AUTHORITY = "CHANNEL_SERVICE";

    private final SecretComparator comparator = new SecretComparator();
    private final List<String> configured;

    public ServiceTokenFilter(ApplicationProperties.TelegramChannel channel) {
        this.configured = java.util.Arrays.asList(channel.currentToken(), channel.nextToken());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String received = request.getHeader(HEADER);
        if (received != null && comparator.containsAny(received, configured)) {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    "channel-service",
                                    null,
                                    List.of(new SimpleGrantedAuthority(AUTHORITY))));
        }
        chain.doFilter(request, response);
    }
}
