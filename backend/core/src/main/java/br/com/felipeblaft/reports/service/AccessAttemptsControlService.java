package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bloqueio por tentativas incorretas, igual nos dois canais (FR-007): a origem é a conta do
 * Telegram ou o IP da Web, guardada só como hash. O bloqueio é verificado antes do código.
 */
public class AccessAttemptsControlService {

    private final AccessAttemptRepository attempts;
    private final DataProtectionPort protection;
    private final ClockPort clock;
    private final BusinessParameters parameters;

    public AccessAttemptsControlService(
            AccessAttemptRepository attempts,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters) {
        this.attempts = Objects.requireNonNull(attempts);
        this.protection = Objects.requireNonNull(protection);
        this.clock = Objects.requireNonNull(clock);
        this.parameters = Objects.requireNonNull(parameters);
    }

    /**
     * Recusa com {@code BLOQUEADO_TEMPORARIAMENTE} (e o tempo restante) se a origem está bloqueada.
     */
    public void verifyLock(Channel channel, String origin) {
        Instant now = clock.now();
        Optional<AccessAttempt> registration = attempts.search(channel, hash(channel, origin));
        if (registration.isEmpty()) {
            return;
        }
        AccessAttempt attempt = registration.get();
        Optional<Duration> remaining = attempt.remainingLock(now);
        if (remaining.isPresent()) {
            long seconds = Math.max(1, (remaining.get().toMillis() + 999) / 1000);
            throw new DomainException(
                    ErrorCode.TEMPORARILY_BLOCKED, Map.of("remainingSeconds", seconds));
        }
        if (attempt.lockEnded(now)) {
            attempt.reset(now);
            attempts.save(attempt);
        }
    }

    public void registerFailure(Channel channel, String origin) {
        Instant now = clock.now();
        String hash = hash(channel, origin);
        AccessAttempt attempt =
                attempts.search(channel, hash)
                        .orElseGet(() -> AccessAttempt.create(channel, hash, now));
        attempt.registerFailure(now, parameters.maxAccessAttempts(), parameters.lockDuration());
        attempts.save(attempt);
    }

    public void registerSuccess(Channel channel, String origin) {
        Instant now = clock.now();
        attempts.search(channel, hash(channel, origin))
                .filter(t -> t.failedAttempts() > 0 || t.blockedUntil() != null)
                .ifPresent(
                        attempt -> {
                            attempt.reset(now);
                            attempts.save(attempt);
                        });
    }

    private String hash(Channel channel, String origin) {
        return protection.originHash(channel, origin == null ? "" : origin);
    }
}
