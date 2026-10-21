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
 * Tentativas de acesso em memória, por {@code (escopo, canal, origem)}. Reproduz a regra da porta:
 * falha durante bloqueio ativo não conta nem prorroga, sucesso não limpa bloqueio ativo e a janela
 * de uma política com janela recomeça quando a primeira falha dela ficou para trás. O registro
 * gravado por {@link #save(AccessAttempt)} (sem escopo) pertence ao escopo do código do professor,
 * o comportamento da 001. O início da janela fica à parte porque {@link AccessAttempt} ainda não o
 * carrega.
 */
public class InMemoryAccessAttemptRepository implements AccessAttemptRepository {

    private static final class Entry {
        final AttemptScope scope;
        AccessAttempt attempt;
        Instant windowStartedAt;

        Entry(AttemptScope scope, AccessAttempt attempt) {
            this.scope = scope;
            this.attempt = attempt;
        }
    }

    private final List<Entry> data = new ArrayList<>();

    @Override
    public Optional<AccessAttempt> search(AttemptScope scope, Channel channel, String originHash) {
        return find(scope, channel, originHash).map(entry -> entry.attempt);
    }

    @Override
    public void save(AccessAttempt attempt) {
        Optional<Entry> existing =
                data.stream().filter(e -> e.attempt.id().equals(attempt.id())).findFirst();
        if (existing.isPresent()) {
            existing.get().attempt = attempt;
        } else {
            data.add(new Entry(AttemptScope.TEACHER_CODE, attempt));
        }
    }

    @Override
    public AccessAttempt registerFailure(
            AttemptScope scope,
            Channel channel,
            String originHash,
            Instant now,
            AttemptPolicy policy) {
        Entry entry =
                find(scope, channel, originHash)
                        .orElseGet(
                                () -> {
                                    Entry created =
                                            new Entry(
                                                    scope,
                                                    AccessAttempt.create(channel, originHash, now));
                                    data.add(created);
                                    return created;
                                });
        AccessAttempt attempt = entry.attempt;
        if (attempt.remainingLock(now).isPresent()) {
            return attempt;
        }
        if (attempt.lockEnded(now)) {
            attempt.reset(now);
            entry.windowStartedAt = null;
        }
        if (policy.hasWindow()
                && entry.windowStartedAt != null
                && !now.isBefore(entry.windowStartedAt.plus(policy.window()))) {
            attempt.reset(now);
            entry.windowStartedAt = null;
        }
        attempt.registerFailure(now, policy.maxAttempts(), policy.lockDuration());
        if (policy.hasWindow() && entry.windowStartedAt == null) {
            entry.windowStartedAt = now;
        }
        return attempt;
    }

    @Override
    public void confirmSuccess(
            AttemptScope scope, Channel channel, String originHash, Instant now) {
        find(scope, channel, originHash)
                .filter(entry -> entry.attempt.remainingLock(now).isEmpty())
                .ifPresent(
                        entry -> {
                            entry.attempt.reset(now);
                            entry.windowStartedAt = null;
                        });
    }

    /** Início da janela de contagem do registro, se a política tiver janela e houver falha nela. */
    public Optional<Instant> windowStartOf(AttemptScope scope, Channel channel, String originHash) {
        return find(scope, channel, originHash).map(entry -> entry.windowStartedAt);
    }

    public List<AccessAttempt> all() {
        return data.stream().map(entry -> entry.attempt).toList();
    }

    private Optional<Entry> find(AttemptScope scope, Channel channel, String originHash) {
        return data.stream()
                .filter(e -> e.scope == scope)
                .filter(e -> e.attempt.channel() == channel)
                .filter(e -> e.attempt.originHash().equals(originHash))
                .findFirst();
    }
}
