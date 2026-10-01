package br.com.felipeblaft.relatorios.porta;

import java.util.function.Supplier;

/**
 * Delimita um trecho atômico no processamento em segundo plano: as etapas longas (transcrição e IA)
 * ficam fora de transação, e cada gravação de estado é atômica e visível aos dois canais.
 */
public interface TransacaoPorta {

    <T> T executar(Supplier<T> trecho);

    default void executar(Runnable trecho) {
        executar(
                () -> {
                    trecho.run();
                    return null;
                });
    }
}
