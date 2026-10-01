package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.porta.TransacaoPorta;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Trechos atômicos do processamento em segundo plano, com o gerenciador de transações do JPA. */
@Component
class TransacaoAdaptador implements TransacaoPorta {

    private final TransactionTemplate modelo;

    TransacaoAdaptador(PlatformTransactionManager gerenciador) {
        this.modelo = new TransactionTemplate(gerenciador);
    }

    @Override
    public <T> T executar(Supplier<T> trecho) {
        return modelo.execute(status -> trecho.get());
    }
}
