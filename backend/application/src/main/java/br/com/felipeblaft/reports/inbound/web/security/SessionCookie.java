package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
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
public class SessionCookie {

    public static final String NAME = "sessao";

    private final boolean safe;

    public SessionCookie(ApplicationProperties properties) {
        this.safe = properties.session().cookieSecure();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
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
                .path("/");
    }

    public static String header() {
        return HttpHeaders.SET_COOKIE;
    }
}
