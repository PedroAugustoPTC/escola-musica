package br.com.felipeblaft.relatorios.entrada.telegram.canal;

/**
 * Corpo de erros 400 da área interna ({@code #/$defs/Erro}). Nunca contém dado de aluno nem eco do
 * conteúdo recebido; {@code 401} e {@code 404} não têm corpo.
 */
public record ErroCanalResposta(String versao, String codigo) {

    public static final String REQUISICAO_INVALIDA = "REQUISICAO_INVALIDA";
    public static final String VERSAO_NAO_SUPORTADA = "VERSAO_NAO_SUPORTADA";

    public static ErroCanalResposta requisicaoInvalida() {
        return new ErroCanalResposta(AtualizacaoResposta.VERSAO, REQUISICAO_INVALIDA);
    }

    public static ErroCanalResposta versaoNaoSuportada() {
        return new ErroCanalResposta(AtualizacaoResposta.VERSAO, VERSAO_NAO_SUPORTADA);
    }
}
