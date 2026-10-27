package br.com.felipeblaft.reports.port;

/**
 * A rotação do segredo do resumo do CPF produziu resumos repetidos (dois alunos com o mesmo CPF
 * depois da troca): a transação é desfeita. Carrega só a contagem, nunca CPF nem resumo.
 */
public class CpfKeyCollisionException extends RuntimeException {

    private final int collisions;

    public CpfKeyCollisionException(int collisions) {
        super(
                "A rotação gerou " + collisions + " colisão(ões) de resumo de CPF",
                null,
                false,
                false);
        this.collisions = collisions;
    }

    public int collisions() {
        return collisions;
    }
}
