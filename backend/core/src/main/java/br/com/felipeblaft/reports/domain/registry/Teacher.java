package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Professor (data-model.md §1). O código de acesso é guardado só como hash. O {@code toString} não
 * revela dado pessoal.
 */
public class Teacher {

    private final UUID id;
    private String name;
    private String email;
    private boolean active;
    private String accessCodeHash;
    private Instant accessCodeExpiresAt;
    private Instant accessCodeRenewedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public Teacher(
            UUID id,
            String name,
            String email,
            boolean active,
            String accessCodeHash,
            Instant accessCodeExpiresAt,
            Instant accessCodeRenewedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
        this.active = active;
        this.accessCodeHash = accessCodeHash;
        this.accessCodeExpiresAt = accessCodeExpiresAt;
        this.accessCodeRenewedAt = accessCodeRenewedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Teacher create(String name, String email, boolean active, Instant now) {
        return new Teacher(
                GeneratorId.create(),
                name,
                normalizeEmail(email),
                active,
                null,
                null,
                null,
                now,
                now);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Código presente, dentro da validade e professor ativo. */
    public boolean validCodeAt(Instant instant) {
        return active
                && accessCodeHash != null
                && accessCodeExpiresAt != null
                && instant.isBefore(accessCodeExpiresAt);
    }

    public boolean codeExpiredAt(Instant instant) {
        return accessCodeHash != null
                && accessCodeExpiresAt != null
                && !instant.isBefore(accessCodeExpiresAt);
    }

    /**
     * Renova a validade do código (FR-005). Para limitar escritas, só renova se a última renovação
     * tiver 1 hora ou mais. Devolve {@code true} se o estado mudou e precisa ser gravado.
     */
    public boolean renewValidity(Instant now, Duration validity) {
        if (accessCodeHash == null) {
            return false;
        }
        if (accessCodeRenewedAt != null
                && Duration.between(accessCodeRenewedAt, now)
                                .compareTo(BusinessParameters.CODE_RENEWAL_INTERVAL)
                        < 0) {
            return false;
        }
        accessCodeExpiresAt = now.plus(validity);
        accessCodeRenewedAt = now;
        updatedAt = now;
        return true;
    }

    /** Emite ou reemite o código: substitui o hash e reinicia a validade. */
    public void replaceCode(String newHash, Instant now, Duration validity) {
        accessCodeHash = Objects.requireNonNull(newHash);
        accessCodeExpiresAt = now.plus(validity);
        accessCodeRenewedAt = now;
        updatedAt = now;
    }

    /** Atualiza nome e situação pela carga; devolve {@code true} se algo mudou. */
    public boolean updateRegistry(String newName, boolean newActive, Instant now) {
        if (name.equals(newName) && active == newActive) {
            return false;
        }
        name = newName;
        active = newActive;
        updatedAt = now;
        return true;
    }

    /**
     * Inativa o professor e invalida o código de acesso (hash, validade e renovação nulos). Devolve
     * {@code true} se mudou.
     */
    public boolean inactivate(Instant now) {
        if (!active) {
            return false;
        }
        active = false;
        accessCodeHash = null;
        accessCodeExpiresAt = null;
        accessCodeRenewedAt = null;
        updatedAt = Objects.requireNonNull(now);
        return true;
    }

    /**
     * Reativa o professor sem código: o caso de uso emite um código novo; sessões e vínculo não
     * voltam. Devolve {@code true} se mudou.
     */
    public boolean reactivate(Instant now) {
        if (active) {
            return false;
        }
        active = true;
        updatedAt = Objects.requireNonNull(now);
        return true;
    }

    public void rename(String newName, Instant now) {
        name = Objects.requireNonNull(newName);
        updatedAt = Objects.requireNonNull(now);
    }

    /** Troca o e-mail, normalizado (sem espaços nas pontas, em minúsculas). */
    public void changeEmail(String newEmail, Instant now) {
        email = Objects.requireNonNull(normalizeEmail(newEmail));
        updatedAt = Objects.requireNonNull(now);
    }

    /** Primeiro nome, usado na saudação ("Olá, Clara"). */
    public String firstName() {
        return name.strip().split("\\s+")[0];
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public boolean active() {
        return active;
    }

    public String accessCodeHash() {
        return accessCodeHash;
    }

    public Instant accessCodeExpiresAt() {
        return accessCodeExpiresAt;
    }

    public Instant accessCodeRenewedAt() {
        return accessCodeRenewedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Teacher[id=" + id + "]";
    }
}
