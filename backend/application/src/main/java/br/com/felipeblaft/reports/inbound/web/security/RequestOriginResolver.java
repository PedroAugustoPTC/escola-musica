package br.com.felipeblaft.reports.inbound.web.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import org.springframework.stereotype.Component;

/**
 * Endereço de origem para o controle de tentativas (FR-007). O {@code X-Forwarded-For} só é aceito
 * quando a conexão vem do proxy na rede interna (Caddy), que substitui o cabeçalho pelo endereço
 * real do cliente; conexões de fora da rede privada usam o próprio endereço remoto.
 */
@Component
public class RequestOriginResolver {

    public String resolver(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && internalNetwork(remote)) {
            return forwarded.split(",")[0].strip();
        }
        return remote;
    }

    static boolean internalNetwork(String address) {
        if (address == null) {
            return false;
        }
        try {
            InetAddress ip = InetAddress.ofLiteral(address);
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress();
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
