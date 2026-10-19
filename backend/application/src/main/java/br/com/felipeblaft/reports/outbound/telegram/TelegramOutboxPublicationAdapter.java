package br.com.felipeblaft.reports.outbound.telegram;

import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.inbound.telegram.TelegramActionPublicationPort;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.channel.ChannelActionMapper;
import br.com.felipeblaft.reports.inbound.telegram.channel.ChannelActionMapper.MappedAction;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entrega proativa pelo orquestrador: as notificações viram ações v1 gravadas na outbox, de forma
 * idempotente por evento (FR-014), e o despachante as entrega ao n8n com retentativa. O backend
 * nunca envia mensagem direto à API de bots (FR-019).
 */
@Component
public class TelegramOutboxPublicationAdapter implements TelegramActionPublicationPort {

    private final ChannelActionMapper mapper;
    private final SaveChannelActionsUseCase save;

    public TelegramOutboxPublicationAdapter(
            ChannelActionMapper mapper, SaveChannelActionsUseCase save) {
        this.mapper = mapper;
        this.save = save;
    }

    @Override
    public void publish(String eventId, UUID teacherId, List<TelegramOutput> outputs) {
        if (outputs.isEmpty()) {
            return;
        }
        String batchId = "p:" + eventId;
        long chat = chatOf(outputs.get(0));
        List<MappedAction> actions = mapper.map(outputs, chat, pos -> batchId + ":" + pos);
        save.run(
                batchId,
                ChannelActionOrigin.PROACTIVE,
                null,
                actions.stream().map(a -> a.toNewAction(teacherId)).toList());
    }

    private static long chatOf(TelegramOutput output) {
        return switch (output) {
            case TelegramOutput.Message m -> m.chatId();
            case TelegramOutput.Edit e -> e.chatId();
            case TelegramOutput.Document d -> d.chatId();
            case TelegramOutput.StepMessage e -> e.chatId();
            case TelegramOutput.CallbackReply r -> 0L;
        };
    }
}
