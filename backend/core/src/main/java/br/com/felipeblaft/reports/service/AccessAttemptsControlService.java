package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bloqueio por tentativas incorretas, igual nos dois canais (FR-007) e separado por escopo (R-05):
 * código do professor, entrada e ativação do administrador, recusas e consultas de CPF do
 * professor. A origem (ou a chave) é guardada só como hash. O bloqueio é verificado antes da
 * credencial; a contagem e o bloqueio da linha são do repositório, a regra é do domínio.
 */
public class AccessAttemptsControlService {

    /** Teto padrão de recusas por CPF de aluno alheio: 5 em 15 minutos, bloqueio de 15 (R-06). */
    static final AttemptPolicy DEFAULT_CPF_PROBE =
            AttemptPolicy.windowed(5, Duration.ofMinutes(15), Duration.ofMinutes(15));

    /** Teto padrão de consultas de CPF digitado: 30 em 60 minutos, bloqueio de 60 (R-06). */
    static final AttemptPolicy DEFAULT_CPF_LOOKUP =
            AttemptPolicy.windowed(30, Duration.ofMinutes(60), Duration.ofMinutes(60));

    private final AccessAttemptRepository attempts;
    private final DataProtectionPort protection;
    private final ClockPort clock;
    private final Map<AttemptScope, AttemptPolicy> policies;

    public AccessAttemptsControlService(
            AccessAttemptRepository attempts,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters) {
        this(attempts, protection, clock, parameters, Map.of());
    }

    /** {@code overrides} substitui a política padrão apenas dos escopos que traz. */
    public AccessAttemptsControlService(
            AccessAttemptRepository attempts,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters,
            Map<AttemptScope, AttemptPolicy> overrides) {
        this.attempts = Objects.requireNonNull(attempts);
        this.protection = Objects.requireNonNull(protection);
        this.clock = Objects.requireNonNull(clock);
        Map<AttemptScope, AttemptPolicy> merged =
                defaultPolicies(Objects.requireNonNull(parameters));
        merged.putAll(Objects.requireNonNull(overrides));
        this.policies = merged;
    }

    /** Políticas padrão por escopo: código, entrada e ativação seguem os parâmetros de negócio. */
    public static Map<AttemptScope, AttemptPolicy> defaultPolicies(BusinessParameters parameters) {
        AttemptPolicy consecutive =
                AttemptPolicy.consecutive(
                        parameters.maxAccessAttempts(), parameters.lockDuration());
        Map<AttemptScope, AttemptPolicy> defaults = new EnumMap<>(AttemptScope.class);
        defaults.put(AttemptScope.TEACHER_CODE, consecutive);
        defaults.put(AttemptScope.ADMIN_SIGN_IN, consecutive);
        defaults.put(AttemptScope.ADMIN_ACTIVATION, consecutive);
        defaults.put(AttemptScope.TEACHER_CPF_PROBE, DEFAULT_CPF_PROBE);
        defaults.put(AttemptScope.TEACHER_CPF_LOOKUP, DEFAULT_CPF_LOOKUP);
        return defaults;
    }

    // ---- código de acesso do professor (001)

    /** Recusa com {@code TEMPORARILY_BLOCKED} (e o tempo restante) se a origem está bloqueada. */
    public void verifyLock(Channel channel, String origin) {
        verifyLock(AttemptScope.TEACHER_CODE, channel, origin);
    }

    public void registerFailure(Channel channel, String origin) {
        registerFailure(AttemptScope.TEACHER_CODE, channel, origin);
    }

    public void registerSuccess(Channel channel, String origin) {
        confirmSuccess(AttemptScope.TEACHER_CODE, channel, origin);
    }

    // ---- por escopo

    /**
     * Recusa se a chave está bloqueada no escopo: {@code TEMPORARILY_BLOCKED} (entrada, ativação,
     * código) ou {@code OPERATION_TEMPORARILY_BLOCKED} (CPF), com o tempo restante em segundos. Só
     * lê: o fim do bloqueio é tratado ao registrar a próxima falha ou o próximo sucesso.
     */
    public void verifyLock(AttemptScope scope, Channel channel, String key) {
        Instant now = clock.now();
        Optional<Duration> remaining =
                attempts.search(scope, channel, hash(scope, channel, key))
                        .flatMap(attempt -> attempt.remainingLock(now));
        if (remaining.isPresent()) {
            long seconds = Math.max(1, (remaining.get().toMillis() + 999) / 1000);
            throw new DomainException(blockedCode(scope), Map.of("remainingSeconds", seconds));
        }
    }

    /**
     * Conta uma falha (ou, em {@code TEACHER_CPF_LOOKUP}, uma consulta) de forma atômica e devolve
     * o estado depois dela. Durante um bloqueio ativo não conta nem prorroga.
     */
    public AccessAttempt registerFailure(AttemptScope scope, Channel channel, String key) {
        return attempts.registerFailure(
                scope, channel, hash(scope, channel, key), clock.now(), policies.get(scope));
    }

    /**
     * Confirma um sucesso: zera a contagem, exceto sob bloqueio ativo. Nos escopos com janela o
     * sucesso não zera nada (uma sondagem esperta alterna acertos e erros, R-06).
     */
    public void confirmSuccess(AttemptScope scope, Channel channel, String key) {
        if (policies.get(scope).hasWindow()) {
            return;
        }
        attempts.confirmSuccess(scope, channel, hash(scope, channel, key), clock.now());
    }

    private String hash(AttemptScope scope, Channel channel, String key) {
        String value = key == null ? "" : key;
        return scope == AttemptScope.TEACHER_CODE
                ? protection.originHash(channel, value)
                : protection.originHash(scope, value);
    }

    private static ErrorCode blockedCode(AttemptScope scope) {
        return switch (scope) {
            case TEACHER_CPF_PROBE, TEACHER_CPF_LOOKUP -> ErrorCode.OPERATION_TEMPORARILY_BLOCKED;
            default -> ErrorCode.TEMPORARILY_BLOCKED;
        };
    }
}
