package br.com.felipeblaft.reports.entrada.web.seguranca;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie {@code sessao}: token opaco, {@code HttpOnly; Secure; SameSite=Strict; Path=/} (R-12). A
 * expiração é controlada no servidor (deslizante), então o cookie é de sessão do navegador.
 */
@Component
public class CookieSessao {

    public static final String NOME = "sessao";

    private final boolean seguro;

    public CookieSessao(PropriedadesAplicacao propriedades) {
        this.seguro = propriedades.sessao().cookieSeguro();
    }

    public String ler(HttpServletRequest requisicao) {
        Cookie[] cookies = requisicao.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NOME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String criar(String token) {
        return base(token).build().toString();
    }

    public String remover() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(seguro)
                .sameSite("Strict")
                .path("/");
    }

    public static String cabecalho() {
        return HttpHeaders.SET_COOKIE;
    }
}
