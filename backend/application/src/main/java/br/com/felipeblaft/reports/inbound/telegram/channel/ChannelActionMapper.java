package br.com.felipeblaft.reports.inbound.telegram.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.inbound.telegram.DocumentReference;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramPresenter;
import br.com.felipeblaft.reports.inbound.telegram.channel.ActionDto.ButtonDto;
import br.com.felipeblaft.reports.inbound.telegram.channel.ActionDto.CallbackReplyActionDto;
import br.com.felipeblaft.reports.inbound.telegram.channel.ActionDto.DocumentActionDto;
import br.com.felipeblaft.reports.inbound.telegram.channel.ActionDto.EditActionDto;
import br.com.felipeblaft.reports.inbound.telegram.channel.ActionDto.KeyboardDto;
import br.com.felipeblaft.reports.inbound.telegram.channel.ActionDto.MessageActionDto;
import br.com.felipeblaft.reports.inbound.telegram.channel.ActionDto.StepMessageActionDto;
import br.com.felipeblaft.reports.usecase.channel.EmitChannelDocumentUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Traduz as saídas do interpretador ({@link TelegramOutput}) para ações v1 (R-03). É só formato:
 * não tem regra de conversa (FR-005). Textos acima do limite do Telegram são divididos aqui (uma
 * mensagem por parte, teclado só na última), os ids são atribuídos <em>depois</em> da expansão, o
 * PDF vira um identificador opaco e a compensação de cada edição é gerada junto.
 */
@Component
public class ChannelActionMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PDF = "application/pdf";

    /**
     * @param noResposta a ação completa (com {@code acaoId}), como vai na resposta HTTP
     * @param payload a ação sem {@code acaoId}, como é guardada na outbox
     * @param compensacao a ação alternativa se a execução falhar por mensagem não editável, ou nulo
     */
    public record MappedAction(
            String actionId,
            ObjectNode replyNode,
            ChannelActionType type,
            long chatId,
            UUID jobId,
            String payload,
            String compensation) {

        /** Ação pronta para a outbox. */
        public NewAction toNewAction(UUID teacherId) {
            return new NewAction(actionId, type, chatId, teacherId, jobId, payload, compensation);
        }
    }

    private final EmitChannelDocumentUseCase emitDocument;

    public ChannelActionMapper(EmitChannelDocumentUseCase emitDocument) {
        this.emitDocument = emitDocument;
    }

    /**
     * @param chatDoUpdate chat de uma {@code RespostaCallback}, que não carrega chat próprio
     * @param idDaPosicao gera o {@code acaoId} a partir da posição na lista já expandida
     */
    public List<MappedAction> map(
            List<TelegramOutput> outputs, long chatDoUpdate, IntFunction<String> positionId) {
        List<MappedAction> actions = new ArrayList<>();
        for (TelegramOutput output : outputs) {
            switch (output) {
                case TelegramOutput.Message m -> message(actions, m, positionId);
                case TelegramOutput.Edit e -> edit(actions, e, positionId);
                case TelegramOutput.Document d -> document(actions, d, positionId);
                case TelegramOutput.StepMessage e -> step(actions, e, positionId);
                case TelegramOutput.CallbackReply r ->
                        callback(actions, r, chatDoUpdate, positionId);
            }
        }
        return actions;
    }

    /** Reconstitui a ação completa a partir do {@code payload} guardado (reentrega e despacho). */
    public static ObjectNode withActionId(String actionId, String payload) {
        ObjectNode complete = JSON.createObjectNode();
        complete.put("actionId", actionId);
        JsonNode read = JSON.readTree(payload);
        if (read instanceof ObjectNode object) {
            complete.setAll(object);
        }
        return complete;
    }

    // ---- por tipo

    private void message(
            List<MappedAction> actions, TelegramOutput.Message m, IntFunction<String> id) {
        List<String> parts = TelegramPresenter.splitText(m.text());
        for (int i = 0; i < parts.size(); i++) {
            boolean last = i == parts.size() - 1;
            KeyboardDto keyboard = last ? keyboard(m.keyboard(), false) : null;
            String actionId = id.apply(actions.size());
            actions.add(
                    mount(
                            new MessageActionDto(
                                    actionId,
                                    ActionDto.MESSAGE_TYPE,
                                    m.chatId(),
                                    parts.get(i),
                                    keyboard),
                            ChannelActionType.MESSAGE,
                            m.chatId(),
                            null,
                            null));
        }
    }

    private void edit(List<MappedAction> actions, TelegramOutput.Edit e, IntFunction<String> id) {
        String actionId = id.apply(actions.size());
        MessageActionDto compensation =
                new MessageActionDto(
                        null,
                        ActionDto.MESSAGE_TYPE,
                        e.chatId(),
                        e.text(),
                        keyboard(e.keyboard(), false));
        actions.add(
                mount(
                        new EditActionDto(
                                actionId,
                                ActionDto.EDIT_TYPE,
                                e.chatId(),
                                e.messageId(),
                                e.text(),
                                keyboard(e.keyboard(), true)),
                        ChannelActionType.EDIT,
                        e.chatId(),
                        null,
                        withoutActionId(compensation)));
    }

    private void document(
            List<MappedAction> actions, TelegramOutput.Document d, IntFunction<String> id) {
        DocumentReference reference = d.reference();
        if (reference == null) {
            throw new IllegalStateException("documento sem referencia para o canal interno");
        }
        String documentId =
                emitDocument.run(
                        reference.teacherId(),
                        reference.type(),
                        reference.reportId(),
                        reference.version(),
                        d.fileName());
        actions.add(
                mount(
                        new DocumentActionDto(
                                id.apply(actions.size()),
                                ActionDto.DOCUMENT_TYPE,
                                d.chatId(),
                                documentId,
                                d.fileName(),
                                PDF,
                                d.caption()),
                        ChannelActionType.DOCUMENT,
                        d.chatId(),
                        null,
                        documentCompensation(d)));
    }

    /**
     * Alternativa para {@code DOCUMENTO_INDISPONIVEL} (FR-007c): só o aviso e o botão, sem o
     * documento nem o texto do relatório.
     */
    private static String documentCompensation(TelegramOutput.Document d) {
        TelegramOutput.Message notice = d.compensation();
        if (notice == null) {
            return null;
        }
        return withoutActionId(
                new MessageActionDto(
                        null,
                        ActionDto.MESSAGE_TYPE,
                        notice.chatId(),
                        notice.text(),
                        keyboard(notice.keyboard(), false)));
    }

    private void step(
            List<MappedAction> actions, TelegramOutput.StepMessage e, IntFunction<String> id) {
        // Numa resposta a update o interpretador já sabe a mensagem a editar (a dos botões); numa
        // ação
        // proativa o despacho a resolve de novo pelo trabalho (a mais recente).
        StepMessageActionDto dto =
                new StepMessageActionDto(
                        id.apply(actions.size()),
                        ActionDto.STEP_MESSAGE_TYPE,
                        e.chatId(),
                        e.text(),
                        e.jobId().toString(),
                        e.editMessageId());
        // a compensação é a mesma ação sem editarMensagemId: envia mensagem nova e regrava o id
        StepMessageActionDto withoutEdit =
                new StepMessageActionDto(
                        null,
                        ActionDto.STEP_MESSAGE_TYPE,
                        e.chatId(),
                        e.text(),
                        e.jobId().toString(),
                        null);
        actions.add(
                mount(
                        dto,
                        ChannelActionType.STEP_MESSAGE,
                        e.chatId(),
                        e.jobId(),
                        withoutActionId(withoutEdit)));
    }

    private void callback(
            List<MappedAction> actions,
            TelegramOutput.CallbackReply r,
            long chatDoUpdate,
            IntFunction<String> id) {
        actions.add(
                mount(
                        new CallbackReplyActionDto(
                                id.apply(actions.size()),
                                ActionDto.CALLBACK_REPLY_TYPE,
                                r.callbackId()),
                        ChannelActionType.CALLBACK_REPLY,
                        chatDoUpdate,
                        null,
                        null));
    }

    // ---- apoio

    private static MappedAction mount(
            ActionDto dto, ChannelActionType type, long chatId, UUID jobId, String compensation) {
        ObjectNode response = JSON.valueToTree(dto);
        ObjectNode withoutId = response.deepCopy();
        withoutId.remove("actionId");
        return new MappedAction(
                dto.actionId(), response, type, chatId, jobId, withoutId.toString(), compensation);
    }

    private static String withoutActionId(ActionDto dto) {
        ObjectNode no = JSON.valueToTree(dto);
        no.remove("actionId");
        return no.toString();
    }

    /** Teclado nulo ou vazio some do JSON; numa edição vazio é explícito (remove os botões). */
    private static KeyboardDto keyboard(TelegramOutput.Keyboard keyboard, boolean explicitIfEmpty) {
        if (keyboard == null || keyboard.empty()) {
            return explicitIfEmpty ? new KeyboardDto(List.of()) : null;
        }
        return new KeyboardDto(
                keyboard.rows().stream()
                        .map(
                                line ->
                                        line.stream()
                                                .map(b -> new ButtonDto(b.label(), b.data()))
                                                .toList())
                        .toList());
    }
}
