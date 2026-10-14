package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.inbound.internal.security.SecretComparator;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada do canal Telegram (contracts/telegram-conversa.md §1): chamado pelo servidor local da API
 * de bots na rede interna. Valida o segredo em tempo constante, deixa o processamento (dedup e
 * interpretação) ao {@link TelegramUpdateProcessorService} e responde sempre 200 depois de gravar o
 * estado; as mensagens do bot são enviadas depois de o estado estar gravado.
 */
@RestController
public class TelegramWebhookController {

    public static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final String secret;
    private final SecretComparator comparator = new SecretComparator();
    private final TelegramUpdateProcessorService processor;
    private final TelegramSender sending;

    public TelegramWebhookController(
            ApplicationProperties properties,
            TelegramUpdateProcessorService processor,
            TelegramSender sending) {
        this.secret = properties.telegram().webhookSecret();
        this.processor = processor;
        this.sending = sending;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = SECRET_HEADER, required = false) String receivedSecret,
            @RequestBody(required = false) String body) {
        if (!comparator.equal(receivedSecret, secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<UpdateTelegram> update = processor.read(body);
        if (update.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        TelegramUpdateResult result = processor.processWebhook(update.get());
        for (TelegramOutput output : result.outputs()) {
            sending.send(output);
        }
        return ResponseEntity.ok().build();
    }
}
