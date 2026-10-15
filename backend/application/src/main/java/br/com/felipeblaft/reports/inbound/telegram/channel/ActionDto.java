package br.com.felipeblaft.reports.inbound.telegram.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Ação v1 que o orquestrador executa no chat (contracts/acoes-v1.schema.json, {@code
 * #/$defs/Acao}). Discriminada pelo campo {@code tipo}. O formato é normativo: nenhum campo de
 * mais, nenhum de menos; campos nulos não são emitidos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface ActionDto {

    String MESSAGE_TYPE = "MESSAGE";
    String EDIT_TYPE = "EDIT";
    String DOCUMENT_TYPE = "DOCUMENT";
    String CALLBACK_REPLY_TYPE = "CALLBACK_REPLY";
    String STEP_MESSAGE_TYPE = "STEP_MESSAGE";

    String actionId();

    String type();

    /** Botão do teclado em linha: {@code dados} é o {@code callback_data} (v1:acao:argumento). */
    record ButtonDto(String label, String data) {}

    record KeyboardDto(List<List<ButtonDto>> rows) {}

    record MessageActionDto(
            String actionId, String type, long chatId, String text, KeyboardDto keyboard)
            implements ActionDto {}

    record EditActionDto(
            String actionId,
            String type,
            long chatId,
            long messageId,
            String text,
            KeyboardDto keyboard)
            implements ActionDto {}

    record DocumentActionDto(
            String actionId,
            String type,
            long chatId,
            String documentId,
            String fileName,
            String contentType,
            String caption)
            implements ActionDto {}

    record CallbackReplyActionDto(String actionId, String type, String callbackId)
            implements ActionDto {}

    record StepMessageActionDto(
            String actionId,
            String type,
            long chatId,
            String text,
            String jobId,
            Long editMessageId)
            implements ActionDto {}
}
