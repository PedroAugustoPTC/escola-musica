package br.com.felipeblaft.reports.outbound;

/**
 * Marca os métodos de adaptador que a Fundação da feature 005 só declara (stubs compiláveis): o
 * fluxo dono de cada um troca o corpo pela implementação real e, no Fecho, esta classe deixa de ter
 * usos e é removida.
 */
public final class PendingImplementation {

    private PendingImplementation() {}

    /** Exceção padrão dos stubs; {@code owner} é o fluxo que implementa (ex.: "W-E"). */
    public static UnsupportedOperationException of(String owner) {
        return new UnsupportedOperationException(
                "Método ainda não implementado (stub da fundação da feature 005; fluxo "
                        + owner
                        + ").");
    }
}
