package br.com.felipeblaft.reports.entrada.interno.seguranca;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
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
public class TokenServicoFiltro extends OncePerRequestFilter {

    public static final String CABECALHO = "X-N8N-Service-Token";
    public static final String AUTORIDADE = "SERVICO_CANAL";

    private final ComparadorSegredo comparador = new ComparadorSegredo();
    private final List<String> configurados;

    public TokenServicoFiltro(PropriedadesAplicacao.CanalTelegram canal) {
        this.configurados = java.util.Arrays.asList(canal.tokenAtual(), canal.tokenProximo());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String recebido = requisicao.getHeader(CABECALHO);
        if (recebido != null && comparador.contemAlgum(recebido, configurados)) {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    "servico-canal",
                                    null,
                                    List.of(new SimpleGrantedAuthority(AUTORIDADE))));
        }
        cadeia.doFilter(requisicao, resposta);
    }
}
