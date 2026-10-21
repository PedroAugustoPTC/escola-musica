package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie {@code admin_session} do administrador (R-03, R-19): token opaco, {@code HttpOnly; Secure;
 * SameSite=Strict; Path=/api/v1/admin}, sem {@code Max-Age} (cookie de sessão do navegador; a
 * validade fixa de duas horas é decidida no servidor). O {@code Path} restrito impede o navegador
 * de enviar a credencial de poder total a qualquer rota que não seja administrativa.
 */
@Component
public class AdminSessionCookie {

    public static final String NAME = "admin_session";
    public static final String PATH = "/api/v1/admin";

    private final boolean safe;

    public AdminSessionCookie(ApplicationProperties properties) {
        this.safe = properties.session().cookieSecure();
    }

    /** Valor do cookie da requisição, ou {@code null} se não veio ou veio vazio. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String create(String token) {
        return base(token).build().toString();
    }

    public String remove() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(safe)
                .sameSite("Strict")
                .path(PATH);
    }

    public static String header() {
        return HttpHeaders.SET_COOKIE;
    }
}
