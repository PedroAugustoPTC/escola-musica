package br.com.felipeblaft.reports.configuracao;

import br.com.felipeblaft.reports.entrada.telegram.PublicacaoAcaoTelegramPorta;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.context.annotation.Configuration;

/**
 * Validação, na subida, da configuração do canal Telegram interno: configuração inconsistente
 * derruba a aplicação em vez de perder mensagens em silêncio. As mensagens de erro nunca repetem a
 * URL do orquestrador (pode conter segredo no caminho) nem token algum.
 */
@Configuration(proxyBeanMethods = false)
public class CanalTelegramConfiguracao {

    public CanalTelegramConfiguracao(
            PropriedadesAplicacao propriedades, List<PublicacaoAcaoTelegramPorta> publicacoes) {
        validar(propriedades.canalTelegram(), publicacoes.size());
    }

    static void validar(PropriedadesAplicacao.CanalTelegram canal, int publicacoesAtivas) {
        if (publicacoesAtivas != 1) {
            throw new IllegalStateException(
                    "exatamente um caminho de entrega proativa deve estar ativo (DIRETA ou"
                            + " ORQUESTRADOR), mas há "
                            + publicacoesAtivas);
        }
        long janelaHttpMs = (long) canal.conexaoTimeoutMs() + canal.respostaTimeoutMs();
        if (canal.leaseSegundos() * 1000L <= janelaHttpMs) {
            throw new IllegalStateException(
                    "relatorios.canal-telegram.lease-segundos deve ser maior que"
                            + " conexao-timeout-ms + resposta-timeout-ms");
        }
        if (canal.entregaProativa() == PropriedadesAplicacao.EntregaProativa.ORQUESTRADOR
                && !canal.orquestradorConfigurado()) {
            throw new IllegalStateException(
                    "CANAL_TELEGRAM_ENTREGA=ORQUESTRADOR exige CANAL_TELEGRAM_ORQUESTRADOR_URL");
        }
        if (canal.orquestradorConfigurado() && !destinoPermitido(canal.orquestradorUrl())) {
            throw new IllegalStateException(
                    "CANAL_TELEGRAM_ORQUESTRADOR_URL deve usar https ou apontar para a rede interna"
                            + " de serviços (nome de serviço sem ponto, localhost ou IP privado)");
        }
    }

    /** {@code https}, ou {@code http} só para a rede interna (Princípio IV). */
    static boolean destinoPermitido(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException invalida) {
            return false;
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        if (esquema.equals("https")) {
            return true;
        }
        return esquema.equals("http") && redeInterna(host);
    }

    private static boolean redeInterna(String host) {
        if (host.equalsIgnoreCase("localhost")) {
            return true;
        }
        if (!host.contains(".") && !host.contains(":")) {
            return true; // nome de serviço do compose, sem domínio
        }
        if (!host.matches("^[0-9.]+$") && !host.contains(":")) {
            return false; // nome com domínio: público
        }
        try {
            InetAddress endereco = InetAddress.getByName(host.replace("[", "").replace("]", ""));
            return endereco.isSiteLocalAddress()
                    || endereco.isLoopbackAddress()
                    || endereco.isLinkLocalAddress();
        } catch (UnknownHostException invalido) {
            return false;
        }
    }
}
