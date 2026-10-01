package br.com.felipeblaft.relatorios.entrada.web.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import org.springframework.stereotype.Component;

/**
 * Endereço de origem para o controle de tentativas (FR-007). O {@code X-Forwarded-For} só é aceito
 * quando a conexão vem do proxy na rede interna (Caddy), que substitui o cabeçalho pelo endereço
 * real do cliente; conexões de fora da rede privada usam o próprio endereço remoto.
 */
@Component
public class OrigemRequisicaoResolvedor {

    public String resolver(HttpServletRequest requisicao) {
        String remoto = requisicao.getRemoteAddr();
        String encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank() && redeInterna(remoto)) {
            return encaminhado.split(",")[0].strip();
        }
        return remoto;
    }

    static boolean redeInterna(String endereco) {
        if (endereco == null) {
            return false;
        }
        try {
            InetAddress ip = InetAddress.ofLiteral(endereco);
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress();
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }
}
