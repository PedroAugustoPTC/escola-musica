package br.com.felipeblaft.reports.domain.access;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Administrador (data-model.md §3.1). Esqueleto da Fundação com o estado; o comportamento (ativar,
 * inativar, reativar, trocar a senha) é do fluxo W-A, que pode convertê-lo em classe. A senha só
 * existe como hash adaptativo e o código de ativação só como HMAC. O {@code toString} não revela
 * dado pessoal.
 *
 * @param passwordHash nulo até a ativação e após inativar
 * @param passwordProvisional verdadeiro só para o primeiro administrador até a troca
 * @param passwordProvisionalExpiresAt vencida, a senha provisória não autentica
 */
public record Administrator(
        UUID id,
        String name,
        String email,
        boolean active,
        String passwordHash,
        boolean passwordProvisional,
        Instant passwordProvisionalExpiresAt,
        String activationCodeHash,
        Instant activationCodeExpiresAt,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt) {

    public Administrator {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(email);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    @Override
    public String toString() {
        return "Administrator[id=" + id + "]";
    }
}
