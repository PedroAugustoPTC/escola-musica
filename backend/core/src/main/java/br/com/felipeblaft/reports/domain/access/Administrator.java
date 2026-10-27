package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Administrador (data-model.md §3.1). A senha só existe como hash adaptativo e o código de ativação
 * só como HMAC. A instância é imutável: cada mudança de estado devolve uma instância nova, que quem
 * chama grava. Estados: pendente de ativação (ativo, sem senha, com código), ativado (com senha) e
 * inativo (sem senha nem código). O {@code toString} não revela dado pessoal.
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

    /**
     * E-mail sem espaços nas pontas e em minúsculas: a mesma normalização do cadastro e da entrada.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Administrador novo: ativo, sem senha e com um código de ativação pendente. */
    public static Administrator pending(
            String name,
            String email,
            String activationCodeHash,
            Instant activationCodeExpiresAt,
            Instant now) {
        return new Administrator(
                GeneratorId.create(),
                name,
                normalizeEmail(email),
                true,
                null,
                false,
                null,
                Objects.requireNonNull(activationCodeHash),
                Objects.requireNonNull(activationCodeExpiresAt),
                null,
                now,
                now);
    }

    /** Primeiro administrador: ativado, com senha provisória que vence após {@code validity}. */
    public static Administrator first(
            String name,
            String email,
            String provisionalPasswordHash,
            Duration validity,
            Instant now) {
        Objects.requireNonNull(validity);
        if (validity.isNegative() || validity.isZero()) {
            throw new IllegalArgumentException("validade da senha provisória inválida");
        }
        return new Administrator(
                GeneratorId.create(),
                name,
                normalizeEmail(email),
                true,
                Objects.requireNonNull(provisionalPasswordHash),
                true,
                now.plus(validity),
                null,
                null,
                now,
                now,
                now);
    }

    /** Tem senha definida; inativar apaga a senha, então a conta inativa não é ativada. */
    public boolean activated() {
        return passwordHash != null;
    }

    /** Ativo, sem senha e à espera da ativação. */
    public boolean pendingActivation() {
        return active && passwordHash == null;
    }

    /**
     * Pode autenticar com a senha: ativo, com senha e, se a senha é provisória, dentro da validade.
     */
    public boolean canSignInAt(Instant instant) {
        if (!active || passwordHash == null) {
            return false;
        }
        return !passwordProvisional
                || (passwordProvisionalExpiresAt != null
                        && instant.isBefore(passwordProvisionalExpiresAt));
    }

    /**
     * O resumo informado é o do código pendente, ainda dentro da validade, de conta ativa e ainda
     * sem senha. A comparação é em tempo constante.
     */
    public boolean activationCodeMatches(String hash, Instant instant) {
        if (hash == null || !pendingActivation() || activationCodeHash == null) {
            return false;
        }
        boolean same =
                MessageDigest.isEqual(
                        activationCodeHash.getBytes(StandardCharsets.UTF_8),
                        hash.getBytes(StandardCharsets.UTF_8));
        return same && activationCodeExpiresAt != null && instant.isBefore(activationCodeExpiresAt);
    }

    /** Define a senha pelo código de ativação: o código é consumido (uso único). */
    public Administrator activate(String newPasswordHash, Instant now) {
        if (!pendingActivation()) {
            throw new IllegalStateException("administrador não está pendente de ativação");
        }
        return new Administrator(
                id,
                name,
                email,
                true,
                Objects.requireNonNull(newPasswordHash),
                false,
                null,
                null,
                null,
                now,
                createdAt,
                now);
    }

    /** Troca a senha provisória pela definitiva. */
    public Administrator changePassword(String newPasswordHash, Instant now) {
        if (!active || !passwordProvisional) {
            throw new IllegalStateException("a senha não é provisória");
        }
        return new Administrator(
                id,
                name,
                email,
                true,
                Objects.requireNonNull(newPasswordHash),
                false,
                null,
                activationCodeHash,
                activationCodeExpiresAt,
                activatedAt,
                createdAt,
                now);
    }

    /** Regrava o hash com parâmetros atuais, sem mudar mais nada. */
    public Administrator rehash(String newPasswordHash, Instant now) {
        return new Administrator(
                id,
                name,
                email,
                active,
                Objects.requireNonNull(newPasswordHash),
                passwordProvisional,
                passwordProvisionalExpiresAt,
                activationCodeHash,
                activationCodeExpiresAt,
                activatedAt,
                createdAt,
                now);
    }

    /** Inativa: apaga a senha, a marca de provisória e o código pendente. */
    public Administrator inactivate(Instant now) {
        return new Administrator(
                id, name, email, false, null, false, null, null, null, activatedAt, createdAt, now);
    }

    /** Reativa como pendente de ativação, com um código novo: exige definir a senha de novo. */
    public Administrator reactivate(String codeHash, Instant codeExpiresAt, Instant now) {
        if (active) {
            throw new IllegalStateException("administrador já está ativo");
        }
        return new Administrator(
                id,
                name,
                email,
                true,
                null,
                false,
                null,
                Objects.requireNonNull(codeHash),
                Objects.requireNonNull(codeExpiresAt),
                null,
                createdAt,
                now);
    }

    @Override
    public String toString() {
        return "Administrator[id=" + id + "]";
    }
}
