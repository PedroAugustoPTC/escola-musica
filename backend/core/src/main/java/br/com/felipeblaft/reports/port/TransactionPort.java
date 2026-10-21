package br.com.felipeblaft.reports.port;

import java.util.function.Supplier;

/**
 * Delimita um trecho atômico no processamento em segundo plano: as etapas longas (transcrição e IA)
 * ficam fora de transação, e cada gravação de estado é atômica e visível aos dois canais.
 */
public interface TransactionPort {

    <T> T run(Supplier<T> excerpt);

    /**
     * Executa o trecho e desfaz a transação ao final, devolvendo o resultado (carga inicial com
     * {@code --simulate}): nada do que o trecho gravou permanece.
     */
    <T> T runAndRollBack(Supplier<T> excerpt);

    default void run(Runnable excerpt) {
        run(
                () -> {
                    excerpt.run();
                    return null;
                });
    }
}
