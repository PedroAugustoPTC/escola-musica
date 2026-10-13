package br.com.felipeblaft.reports.port;

/**
 * Resultado da entrega de um lote: aceito (qualquer 2xx) ou recusado com um código de situação sem
 * dado de aluno (status HTTP, {@code TIMEOUT}, {@code CONEXAO}…).
 */
public record DeliveryResult(boolean accepted, String statusCode) {

    public static DeliveryResult accept() {
        return new DeliveryResult(true, "ACEITO");
    }

    public static DeliveryResult refused(String statusCode) {
        return new DeliveryResult(false, statusCode);
    }
}
