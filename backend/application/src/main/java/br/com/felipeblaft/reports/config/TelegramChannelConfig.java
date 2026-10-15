package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.inbound.telegram.TelegramActionPublicationPort;
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
public class TelegramChannelConfig {

    public TelegramChannelConfig(
            ApplicationProperties properties, List<TelegramActionPublicationPort> publications) {
        validate(properties.telegramChannel(), publications.size());
    }

    static void validate(ApplicationProperties.TelegramChannel channel, int activePublications) {
        if (activePublications != 1) {
            throw new IllegalStateException(
                    "exatamente um caminho de entrega proativa deve estar ativo (DIRECT ou"
                            + " ORCHESTRATOR), mas há "
                            + activePublications);
        }
        long windowHttpMs = (long) channel.connectionTimeoutMs() + channel.responseTimeoutMs();
        if (channel.leaseSeconds() * 1000L <= windowHttpMs) {
            throw new IllegalStateException(
                    "reports.telegram-channel.lease-seconds deve ser maior que"
                            + " connection-timeout-ms + response-timeout-ms");
        }
        if (channel.proactiveDelivery() == ApplicationProperties.ProactiveDelivery.ORCHESTRATOR
                && !channel.configuredOrchestrator()) {
            throw new IllegalStateException(
                    "TELEGRAM_CHANNEL_DELIVERY=ORCHESTRATOR exige TELEGRAM_CHANNEL_ORCHESTRATOR_URL");
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
