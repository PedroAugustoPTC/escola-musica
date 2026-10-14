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

    String MESSAGE_TYPE = "MENSAGEM";
    String EDIT_TYPE = "EDICAO";
    String DOCUMENT_TYPE = "DOCUMENTO";
    String CALLBACK_REPLY_TYPE = "RESPOSTA_CALLBACK";
    String STEP_MESSAGE_TYPE = "MENSAGEM_ETAPA";

    String acaoId();

    String tipo();

    /** Botão do teclado em linha: {@code dados} é o {@code callback_data} (v1:acao:argumento). */
    record ButtonDto(String rotulo, String dados) {}

    record KeyboardDto(List<List<ButtonDto>> linhas) {}

    record MessageActionDto(
            String acaoId, String tipo, long chatId, String texto, KeyboardDto teclado)
            implements ActionDto {}

    record EditActionDto(
            String acaoId,
            String tipo,
            long chatId,
            long mensagemId,
            String texto,
            KeyboardDto teclado)
            implements ActionDto {}

    record DocumentActionDto(
            String acaoId,
            String tipo,
            long chatId,
            String documentoId,
            String nomeArquivo,
            String tipoConteudo,
            String legenda)
            implements ActionDto {}

    record CallbackReplyActionDto(String acaoId, String tipo, String callbackId)
            implements ActionDto {}

    record StepMessageActionDto(
            String acaoId,
            String tipo,
            long chatId,
            String texto,
            String trabalhoId,
            Long editarMensagemId)
            implements ActionDto {}
}
