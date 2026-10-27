package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.port.TransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Trechos atômicos do processamento em segundo plano, com o gerenciador de transações do JPA. */
@Component
class TransactionAdapter implements TransactionPort {

    private final TransactionTemplate model;
    private final TransactionTemplate simulation;

    TransactionAdapter(PlatformTransactionManager manager) {
        this.model = new TransactionTemplate(manager);
        this.simulation = new TransactionTemplate(manager);
        // transação própria: o desfazer nunca contamina uma transação externa
        this.simulation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T run(Supplier<T> excerpt) {
        return model.execute(status -> excerpt.get());
    }

    @Override
    public <T> T runAndRollBack(Supplier<T> excerpt) {
        return simulation.execute(
                status -> {
                    status.setRollbackOnly();
                    return excerpt.get();
                });
    }
}
