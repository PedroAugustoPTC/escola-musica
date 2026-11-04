package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.config.log.CorrelationMdc;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dá a cada requisição o identificador de correlação da autoria e o põe no contexto dos logs
 * (FR-050), antes das cadeias de segurança, de modo que até a recusa de autenticação já é logada
 * com o mesmo identificador que o registro de autoria usará. É o único ponto que cria o
 * identificador Web; os demais usam {@link RequestCorrelation#of}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = RequestCorrelation.of(request);
        try (CorrelationMdc scope = CorrelationMdc.open(id)) {
            chain.doFilter(request, response);
        }
    }
}
