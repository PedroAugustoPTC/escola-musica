package br.com.felipeblaft.reports.domain.access;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão do administrador (data-model.md §3.2): validade fixa de 2 horas, sem renovação, revogável.
 * Válida enquanto {@code now < expiresAt}, não revogada e com a conta ativa. Esqueleto da Fundação
 * com o estado; o comportamento é do fluxo W-A. No banco fica só o hash do token.
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

    @Override
    public String toString() {
        return "AdministratorSession[id=" + id + "]";
    }
}
