package br.com.felipeblaft.reports.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.context.annotation.Configuration;

/**
 * Validação, na subida, da configuração do canal Telegram interno: configuração inconsistente
 * derruba a aplicação em vez de perder mensagens em silêncio. Com o Telegram ligado, o orquestrador
 * (n8n) é o único transporte, então o endereço dele é obrigatório. As mensagens de erro nunca
 * repetem a URL do orquestrador (pode conter segredo no caminho) nem token algum.
 */
@Configuration(proxyBeanMethods = false)
public class TelegramChannelConfig {

    public TelegramChannelConfig(ApplicationProperties properties) {
        validate(properties.telegramChannel(), properties.telegram().enabled());
    }

    static void validate(ApplicationProperties.TelegramChannel channel, boolean telegramEnabled) {
        long windowHttpMs = (long) channel.connectionTimeoutMs() + channel.responseTimeoutMs();
        if (channel.leaseSeconds() * 1000L <= windowHttpMs) {
            throw new IllegalStateException(
                    "reports.telegram-channel.lease-seconds deve ser maior que"
                            + " connection-timeout-ms + response-timeout-ms");
        }
        if (telegramEnabled && !channel.configuredOrchestrator()) {
            throw new IllegalStateException(
                    "TELEGRAM_ENABLED=true exige TELEGRAM_CHANNEL_ORCHESTRATOR_URL: o n8n é o único"
                            + " transporte do Telegram");
        }
        if (channel.configuredOrchestrator() && !allowedDestination(channel.orchestratorUrl())) {
            throw new IllegalStateException(
                    "TELEGRAM_CHANNEL_ORCHESTRATOR_URL deve usar https ou apontar para a rede interna"
                            + " de serviços (nome de serviço sem ponto, localhost ou IP privado)");
        }
    }

    /** {@code https}, ou {@code http} só para a rede interna (Princípio IV). */
    static boolean allowedDestination(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        String schema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        if (schema.equals("https")) {
            return true;
        }
        return schema.equals("http") && internalNetwork(host);
    }

    private static boolean internalNetwork(String host) {
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
            InetAddress address = InetAddress.getByName(host.replace("[", "").replace("]", ""));
            return address.isSiteLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress();
        } catch (UnknownHostException invalid) {
            return false;
        }
    }
}
