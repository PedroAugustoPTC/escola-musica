package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tentativas de acesso gravadas numa transação independente ({@code REQUIRES_NEW}): a falha de
 * código fica registrada mesmo quando o caso de uso que a detectou é desfeito (FR-007, lição 4).
 */
@Repository
class AccessAttemptRepositoryAdapter implements AccessAttemptRepository {

    private final AccessAttemptJpa jpa;

    AccessAttemptRepositoryAdapter(AccessAttemptJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<AccessAttempt> search(AttemptScope scope, Channel channel, String originHash) {
        // a tabela ainda não tem a coluna de escopo (V10, fluxo W-F): só o código do professor
        // existe
        if (scope != AttemptScope.TEACHER_CODE) {
            throw PendingImplementation.of("W-F");
        }
        return jpa.findByChannelAndSourceHash(channel, originHash)
                .map(
                        e ->
                                new AccessAttempt(
                                        e.getId(),
                                        e.getChannel(),
                                        e.getSourceHash(),
                                        e.getFailedAttempts(),
                                        e.getBlockedUntil(),
                                        e.getUpdatedAt()));
    }

    @Override
    public AccessAttempt registerFailure(
            AttemptScope scope,
            Channel channel,
            String originHash,
            Instant now,
            AttemptPolicy policy) {
        throw PendingImplementation.of("W-F");
    }

    @Override
    public void confirmSuccess(
            AttemptScope scope, Channel channel, String originHash, Instant now) {
        throw PendingImplementation.of("W-F");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AccessAttempt t) {
        jpa.save(
                new AccessAttemptEntity(
                        t.id(),
                        t.channel(),
                        t.originHash(),
                        t.failedAttempts(),
                        t.blockedUntil(),
                        t.updatedAt()));
    }
}
