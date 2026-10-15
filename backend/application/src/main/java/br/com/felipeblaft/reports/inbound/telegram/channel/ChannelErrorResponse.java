package br.com.felipeblaft.reports.inbound.telegram.channel;

/**
 * Corpo de erros 400 da área interna ({@code #/$defs/Erro}). Nunca contém dado de aluno nem eco do
 * conteúdo recebido; {@code 401} e {@code 404} não têm corpo.
 */
public record ChannelErrorResponse(String version, String code) {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION";

    public static ChannelErrorResponse invalidRequest() {
        return new ChannelErrorResponse(UpdateResponse.VERSION, INVALID_REQUEST);
    }

    public static ChannelErrorResponse unsupportedVersion() {
        return new ChannelErrorResponse(UpdateResponse.VERSION, UNSUPPORTED_VERSION);
    }
}
