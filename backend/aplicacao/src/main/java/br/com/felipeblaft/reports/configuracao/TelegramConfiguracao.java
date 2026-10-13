package br.com.felipeblaft.reports.configuracao;

import br.com.felipeblaft.reports.saida.telegram.MensageriaTelegramAdaptador;
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
@Profile("!carga")
public class TelegramConfiguracao {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramConfiguracao.class);

    private final PropriedadesAplicacao.Telegram telegram;
    private final MensageriaTelegramAdaptador mensageria;
    private final CatalogoMensagens catalogo;
    private final AtomicBoolean registrado = new AtomicBoolean(false);

    public TelegramConfiguracao(
            PropriedadesAplicacao propriedades,
            MensageriaTelegramAdaptador mensageria,
            CatalogoMensagens catalogo) {
        this.telegram = propriedades.telegram();
        this.mensageria = mensageria;
        this.catalogo = catalogo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        registrar();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tentarDeNovo() {
        if (!registrado.get()) {
            registrar();
        }
    }

    void registrar() {
        if (!telegram.habilitado()) {
            registrado.set(true);
            return;
        }
        boolean ok =
                mensageria
                        .chamar(
                                "setWebhook",
                                Map.of(
                                        "url", telegram.webhookUrl(),
                                        "secret_token", telegram.segredoWebhook(),
                                        "allowed_updates", List.of("message", "callback_query"),
                                        "max_connections", 10))
                        .isPresent();
        if (!ok) {
            LOG.warn("webhook-nao-registrado; nova tentativa em 1 minuto");
            return;
        }
        mensageria.chamar(
                "setMyCommands",
                Map.of(
                        "commands",
                        List.of(
                                comando("inicio", "BOTAO_INICIO"),
                                comando("registrar", "BOTAO_REGISTRAR_AULA"),
                                comando("semestral", "BOTAO_RELATORIO_SEMESTRAL"),
                                comando("historico", "BOTAO_HISTORICO"),
                                Map.of("command", "ajuda", "description", "Ajuda"),
                                Map.of("command", "sair", "description", "Sair"))));
        registrado.set(true);
        LOG.info("webhook-registrado");
    }

    private Map<String, String> comando(String nome, String rotulo) {
        return Map.of("command", nome, "description", catalogo.texto(rotulo));
    }
}
