package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Ação v1 que o orquestrador executa no chat (contracts/acoes-v1.schema.json, {@code #/$defs/Acao}).
 * Discriminada pelo campo {@code tipo}. O formato é normativo: nenhum campo de mais, nenhum de menos;
 * campos nulos não são emitidos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface AcaoDto {

    String TIPO_MENSAGEM = "MENSAGEM";
    String TIPO_EDICAO = "EDICAO";
    String TIPO_DOCUMENTO = "DOCUMENTO";
    String TIPO_RESPOSTA_CALLBACK = "RESPOSTA_CALLBACK";
    String TIPO_MENSAGEM_ETAPA = "MENSAGEM_ETAPA";

    String acaoId();

    String tipo();

    /** Botão do teclado em linha: {@code dados} é o {@code callback_data} (v1:acao:argumento). */
    record BotaoDto(String rotulo, String dados) {}

    record TecladoDto(List<List<BotaoDto>> linhas) {}

    record AcaoMensagemDto(String acaoId, String tipo, long chatId, String texto, TecladoDto teclado)
            implements AcaoDto {}

    record AcaoEdicaoDto(
            String acaoId,
            String tipo,
            long chatId,
            long mensagemId,
            String texto,
            TecladoDto teclado)
            implements AcaoDto {}

    record AcaoDocumentoDto(
            String acaoId,
            String tipo,
            long chatId,
            String documentoId,
            String nomeArquivo,
            String tipoConteudo,
            String legenda)
            implements AcaoDto {}

    record AcaoRespostaCallbackDto(String acaoId, String tipo, String callbackId)
            implements AcaoDto {}

    record AcaoMensagemEtapaDto(
            String acaoId,
            String tipo,
            long chatId,
            String texto,
            String trabalhoId,
            Long editarMensagemId)
            implements AcaoDto {}
}
