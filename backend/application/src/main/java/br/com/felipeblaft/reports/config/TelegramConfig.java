package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.outbound.telegram.TelegramMessagingAdapter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Registra o webhook no servidor local da API de bots ao iniciar (com {@code secret_token}) e os
 * comandos do bot. Se o servidor ainda não estiver pronto, tenta de novo a cada minuto.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!seed")
public class TelegramConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramConfig.class);

    private final ApplicationProperties.Telegram telegram;
    private final TelegramMessagingAdapter messaging;
    private final MessageCatalog catalog;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public TelegramConfig(
            ApplicationProperties properties,
            TelegramMessagingAdapter messaging,
            MessageCatalog catalog) {
        this.telegram = properties.telegram();
        this.messaging = messaging;
        this.catalog = catalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        register();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retry() {
        if (!registered.get()) {
            register();
        }
    }

    void register() {
        if (!telegram.enabled()) {
            registered.set(true);
            return;
        }
        boolean ok =
                messaging
                        .call(
                                "setWebhook",
                                Map.of(
                                        "url", telegram.webhookUrl(),
                                        "secret_token", telegram.webhookSecret(),
                                        "allowed_updates", List.of("message", "callback_query"),
                                        "max_connections", 10))
                        .isPresent();
        if (!ok) {
            LOG.warn("webhook-not-registered; nova tentativa em 1 minuto");
            return;
        }
        messaging.call(
                "setMyCommands",
                Map.of(
                        "commands",
                        List.of(
                                command("inicio", "HOME_BUTTON"),
                                command("registrar", "REGISTER_LESSON_BUTTON"),
                                command("semestral", "SEMESTER_REPORT_BUTTON"),
                                command("historico", "HISTORY_BUTTON"),
                                Map.of("command", "ajuda", "description", "Ajuda"),
                                Map.of("command", "sair", "description", "Sair"))));
        registered.set(true);
        LOG.info("webhook-registered");
    }

    private Map<String, String> command(String name, String label) {
        return Map.of("command", name, "description", catalog.text(label));
    }
}
