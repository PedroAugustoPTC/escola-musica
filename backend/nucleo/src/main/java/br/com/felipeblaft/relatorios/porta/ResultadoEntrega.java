package br.com.felipeblaft.relatorios.porta;

/**
 * Resultado da entrega de um lote: aceito (qualquer 2xx) ou recusado com um código de situação sem
 * dado de aluno (status HTTP, {@code TIMEOUT}, {@code CONEXAO}…).
 */
public record ResultadoEntrega(boolean aceito, String codigoSituacao) {

    public static ResultadoEntrega aceite() {
        return new ResultadoEntrega(true, "ACEITO");
    }

    public static ResultadoEntrega recusado(String codigoSituacao) {
        return new ResultadoEntrega(false, codigoSituacao);
    }
}
