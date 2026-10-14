package br.com.felipeblaft.reports.outbound.telegram;

import br.com.felipeblaft.reports.inbound.telegram.TelegramActionPublicationPort;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Caminho atual (padrão): as notificações proativas vão direto à API de bots, na ordem. É o
 * comportamento de antes da feature 002; nada entra na outbox.
 */
@Component
@ConditionalOnProperty(
        prefix = "reports.telegram-channel",
        name = "proactive-delivery",
        havingValue = "DIRETA",
        matchIfMissing = true)
public class TelegramDirectPublicationAdapter implements TelegramActionPublicationPort {

    private final TelegramMessagingAdapter messaging;

    public TelegramDirectPublicationAdapter(TelegramMessagingAdapter messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publish(String eventId, UUID teacherId, List<TelegramOutput> outputs) {
        for (TelegramOutput output : outputs) {
            // uma etapa proativa sem mensagem conhecida não abre mensagem nova: só edita a
            // existente
            if (output instanceof TelegramOutput.StepMessage step && step.editMessageId() == null) {
                continue;
            }
            messaging.send(output);
        }
    }
}
