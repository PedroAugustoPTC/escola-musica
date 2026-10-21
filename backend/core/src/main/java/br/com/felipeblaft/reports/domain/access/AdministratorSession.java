package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão do administrador (data-model.md §3.2): validade fixa de 2 horas, sem renovação, revogável.
 * Válida enquanto {@code now < expiresAt}, não revogada e com a conta ativa. A instância é
 * imutável: {@code expiresAt} nunca muda depois de aberta. No banco fica só o hash do token.
 */
public record AdministratorSession(
        UUID id,
        UUID administratorId,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        SessionRevocationReason revocationReason) {

    public AdministratorSession {
        Objects.requireNonNull(id);
        Objects.requireNonNull(administratorId);
        Objects.requireNonNull(tokenHash);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
    }

    /** Abre uma sessão nova, com identificador novo e validade fixa a partir de {@code now}. */
    public static AdministratorSession open(
            UUID administratorId, String tokenHash, Instant now, Duration validity) {
        Objects.requireNonNull(validity);
        if (validity.isNegative() || validity.isZero()) {
            throw new IllegalArgumentException("validade da sessão inválida");
        }
        return new AdministratorSession(
                GeneratorId.create(),
                administratorId,
                tokenHash,
                now,
                now.plus(validity),
                null,
                null);
    }

    public boolean revoked() {
        return revokedAt != null;
    }

    public boolean expiredAt(Instant instant) {
        return !instant.isBefore(expiresAt);
    }

    /** Válida ⇔ antes do vencimento, não revogada e com a conta ativa. */
    public boolean validAt(Instant instant, boolean accountActive) {
        return accountActive && !revoked() && !expiredAt(instant);
    }

    /** Marca a sessão como revogada; uma segunda revogação mantém a primeira. */
    public AdministratorSession revoke(SessionRevocationReason reason, Instant now) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);
        if (revoked()) {
            return this;
        }
        return new AdministratorSession(
                id, administratorId, tokenHash, createdAt, expiresAt, now, reason);
    }

    @Override
    public String toString() {
        return "AdministratorSession[id=" + id + "]";
    }
}
