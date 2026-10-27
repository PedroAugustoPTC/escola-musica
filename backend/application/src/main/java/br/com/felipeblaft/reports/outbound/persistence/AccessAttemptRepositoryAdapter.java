package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tentativas de acesso gravadas numa transação independente ({@code REQUIRES_NEW}): a falha de
 * código fica registrada mesmo quando o caso de uso que a detectou é desfeito (FR-007, lição 4). A
 * contagem é atômica (FR-041, R-05): cria a linha se faltar ({@code ON CONFLICT DO NOTHING}), lê
 * com {@code SELECT ... FOR UPDATE} e deixa a regra para o domínio; o adaptador só serializa.
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
        return jpa.findByScopeAndChannelAndSourceHash(scope, channel, originHash)
                .map(AccessAttemptEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccessAttempt registerFailure(
            AttemptScope scope,
            Channel channel,
            String originHash,
            Instant now,
            AttemptPolicy policy) {
        jpa.insertIfAbsent(GeneratorId.create(), scope.name(), channel.name(), originHash, now);
        AccessAttemptEntity row = jpa.lockByKey(scope, channel, originHash).orElseThrow();
        AccessAttempt attempt = row.toDomain();
        attempt.registerFailure(now, policy);
        row.copyStateOf(attempt);
        return attempt;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmSuccess(
            AttemptScope scope, Channel channel, String originHash, Instant now) {
        jpa.lockByKey(scope, channel, originHash)
                .ifPresent(
                        row -> {
                            AccessAttempt attempt = row.toDomain();
                            boolean clean =
                                    attempt.failedAttempts() == 0 && attempt.blockedUntil() == null;
                            if (!clean && attempt.confirmSuccess(now)) {
                                row.copyStateOf(attempt);
                            }
                        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AccessAttempt attempt) {
        jpa.save(new AccessAttemptEntity(attempt));
    }
}
