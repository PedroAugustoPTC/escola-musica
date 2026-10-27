package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tentativas de acesso em memória, por {@code (escopo, canal, origem)}. A regra (falha durante
 * bloqueio ativo não conta nem prorroga, sucesso não limpa bloqueio ativo, janela que recomeça) é
 * do próprio {@link AccessAttempt}; o dublê só guarda e delega, como o adaptador real faz sob
 * bloqueio de linha.
 */
public class InMemoryAccessAttemptRepository implements AccessAttemptRepository {

    private final List<AccessAttempt> data = new ArrayList<>();

    @Override
    public Optional<AccessAttempt> search(AttemptScope scope, Channel channel, String originHash) {
        return data.stream()
                .filter(a -> a.scope() == scope)
                .filter(a -> a.channel() == channel)
                .filter(a -> a.originHash().equals(originHash))
                .findFirst();
    }

    @Override
    public void save(AccessAttempt attempt) {
        data.removeIf(existing -> existing.id().equals(attempt.id()));
        data.add(attempt);
    }

    @Override
    public AccessAttempt registerFailure(
            AttemptScope scope,
            Channel channel,
            String originHash,
            Instant now,
            AttemptPolicy policy) {
        AccessAttempt attempt =
                search(scope, channel, originHash)
                        .orElseGet(
                                () -> {
                                    AccessAttempt created =
                                            AccessAttempt.create(scope, channel, originHash, now);
                                    data.add(created);
                                    return created;
                                });
        attempt.registerFailure(now, policy);
        return attempt;
    }

    @Override
    public void confirmSuccess(
            AttemptScope scope, Channel channel, String originHash, Instant now) {
        search(scope, channel, originHash).ifPresent(attempt -> attempt.confirmSuccess(now));
    }

    /** Início da janela de contagem do registro, se a política tiver janela e houver falha nela. */
    public Optional<Instant> windowStartOf(AttemptScope scope, Channel channel, String originHash) {
        return search(scope, channel, originHash).map(AccessAttempt::windowStartedAt);
    }

    public List<AccessAttempt> all() {
        return List.copyOf(data);
    }
}
