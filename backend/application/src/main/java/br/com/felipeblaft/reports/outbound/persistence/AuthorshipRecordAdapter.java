package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registro de autoria (somente inserção, V10) em dois modos (R-10): {@code record} na transação do
 * caso de uso, de modo que registro e operação valem juntos e a falha de um desfaz o outro; {@code
 * recordIndependent} em transação própria, para falhas, bloqueios e negativas cuja transação será
 * desfeita, sem nunca mascarar a recusa original.
 */
@Repository
class AuthorshipRecordAdapter implements AuthorshipRecorderPort {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorshipRecordAdapter.class);

    private final AuthorshipRecordJpa jpa;
    private final TransactionTemplate independent;

    AuthorshipRecordAdapter(AuthorshipRecordJpa jpa, PlatformTransactionManager transactions) {
        this.jpa = jpa;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuthorshipRecord record) {
        // descarrega já: a violação (gatilho, CHECK, chave repetida) surge aqui e desfaz a operação
        jpa.saveAndFlush(new AuthorshipRecordEntity(record));
    }

    @Override
    public void recordIndependent(AuthorshipRecord record) {
        try {
            independent.executeWithoutResult(
                    status -> jpa.saveAndFlush(new AuthorshipRecordEntity(record)));
        } catch (RuntimeException failure) {
            // só o tipo da falha e o código da operação: a mensagem do banco pode citar valores
            LOG.error(
                    "Falha ao gravar o registro de autoria independente (operação {}, causa {}).",
                    record.operation(),
                    failure.getClass().getSimpleName());
        }
    }
}
