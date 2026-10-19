package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.inbound.telegram.TelegramActionPublicationPort;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.outbound.telegram.TelegramOutboxPublicationAdapter;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Publicação proativa dos testes de conversa: grava na outbox como em produção e, em seguida,
 * entrega o que ficou devido ({@link ProactiveOutboxDrain}), no lugar do despachante agendado e do
 * n8n.
 */
@Component
@Primary
@ConditionalOnProperty(name = "test.telegram.drain-proactive", havingValue = "true")
public class DrainingPublicationAdapter implements TelegramActionPublicationPort {

    private final TelegramOutboxPublicationAdapter outbox;
    private final ObjectProvider<ProactiveOutboxDrain> drain;

    public DrainingPublicationAdapter(
            TelegramOutboxPublicationAdapter outbox, ObjectProvider<ProactiveOutboxDrain> drain) {
        this.outbox = outbox;
        this.drain = drain;
    }

    @Override
    public void publish(String eventId, UUID teacherId, List<TelegramOutput> outputs) {
        outbox.publish(eventId, teacherId, outputs);
        drain.getObject().drain();
    }
}
