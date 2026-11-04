package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import jakarta.persistence.EntityManager;
import java.util.List;
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

    /** Chave do travamento que serializa a gravação da cadeia (um evento por vez). */
    private static final long CHAIN_LOCK_KEY = 7_007_001L;

    private final AuthorshipRecordJpa jpa;
    private final EntityManager entityManager;
    private final TransactionTemplate independent;

    AuthorshipRecordAdapter(
            AuthorshipRecordJpa jpa,
            EntityManager entityManager,
            PlatformTransactionManager transactions) {
        this.jpa = jpa;
        this.entityManager = entityManager;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuthorshipRecord record) {
        // descarrega já: a violação (gatilho, CHECK, chave repetida) surge aqui e desfaz a operação
        jpa.saveAndFlush(chained(record));
    }

    /**
     * Encadeia o evento (FR-040): sob {@code pg_advisory_xact_lock}, lê o hash do último evento (ou
     * o da âncora do expurgo, ou o hash inicial), reserva a sequência e calcula o hash do próprio
     * evento com a forma canônica do núcleo ({@link AuditChain}).
     */
    private AuthorshipRecordEntity chained(AuthorshipRecord record) {
        entityManager
                .createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", CHAIN_LOCK_KEY)
                .getSingleResult();
        List<?> last =
                entityManager
                        .createNativeQuery(
                                "select record_hash from authorship_record order by sequence desc limit 1")
                        .getResultList();
        String previous;
        if (!last.isEmpty()) {
            previous = (String) last.getFirst();
        } else {
            List<?> anchor =
                    entityManager
                            .createNativeQuery(
                                    "select last_record_hash from audit_chain_anchor"
                                            + " order by purged_until_sequence desc limit 1")
                            .getResultList();
            previous = anchor.isEmpty() ? AuditChain.GENESIS_HASH : (String) anchor.getFirst();
        }
        long sequence =
                ((Number)
                                entityManager
                                        .createNativeQuery(
                                                "select nextval(pg_get_serial_sequence('authorship_record',"
                                                        + " 'sequence'))")
                                        .getSingleResult())
                        .longValue();
        AuthorshipRecordEntity entity = new AuthorshipRecordEntity(record);
        entity.chain(sequence, previous, AuditChain.hash(previous, entity.chainFields(sequence)));
        return entity;
    }

    @Override
    public void recordIndependent(AuthorshipRecord record) {
        try {
            independent.executeWithoutResult(status -> jpa.saveAndFlush(chained(record)));
        } catch (RuntimeException failure) {
            // só o tipo da falha e o código da operação: a mensagem do banco pode citar valores
            LOG.error(
                    "Falha ao gravar o registro de autoria independente (operação {}, causa {}).",
                    record.operation(),
                    failure.getClass().getSimpleName());
        }
    }
}
