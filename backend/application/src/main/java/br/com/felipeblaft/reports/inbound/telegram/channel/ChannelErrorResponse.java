package br.com.felipeblaft.reports.inbound.telegram.channel;

/**
 * Corpo de erros 400 da área interna ({@code #/$defs/Erro}). Nunca contém dado de aluno nem eco do
 * conteúdo recebido; {@code 401} e {@code 404} não têm corpo.
 */
public record ChannelErrorResponse(String versao, String codigo) {

    public static final String INVALID_REQUEST = "REQUISICAO_INVALIDA";
    public static final String UNSUPPORTED_VERSION = "VERSAO_NAO_SUPORTADA";

    public static ChannelErrorResponse invalidRequest() {
        return new ChannelErrorResponse(UpdateResponse.VERSION, INVALID_REQUEST);
    }

    public static ChannelErrorResponse unsupportedVersion() {
        return new ChannelErrorResponse(UpdateResponse.VERSION, UNSUPPORTED_VERSION);
    }
}
