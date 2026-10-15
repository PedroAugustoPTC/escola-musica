package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
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
    public Optional<AccessAttempt> search(Channel channel, String originHash) {
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
