package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import java.time.Instant;
import java.util.Locale;

/**
 * Administrador ativo com senha conhecida, inserido direto pelo repositório e pelo hash de senha,
 * para os testes de integração entrarem como administrador sem depender do bootstrap (teste
 * independente da US1). A senha é um valor de teste, sem relação com nenhuma senha real; o {@code
 * toString} nunca a revela.
 *
 * @param administrator a conta inserida
 * @param email e-mail normalizado, o de entrada
 * @param password a senha em claro, só para o teste usar na entrada
 */
public record AdministratorFixture(Administrator administrator, String email, Secret password) {

    public static final String DEFAULT_NAME = "Administrador de Teste";
    public static final String DEFAULT_EMAIL = "administrator.test@example.test";
    public static final String DEFAULT_PASSWORD = "long-test-password-1";

    /** Insere o administrador padrão (ativo, ativado e com senha definitiva). */
    public static AdministratorFixture insertActive(
            AdministratorRepository administrators, PasswordHashPort hasher, Instant now) {
        return insertActive(administrators, hasher, now, DEFAULT_NAME, DEFAULT_EMAIL);
    }

    public static AdministratorFixture insertActive(
            AdministratorRepository administrators,
            PasswordHashPort hasher,
            Instant now,
            String name,
            String email) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        Secret password = new Secret(DEFAULT_PASSWORD);
        Administrator administrator =
                new Administrator(
                        GeneratorId.create(),
                        name,
                        normalizedEmail,
                        true,
                        hasher.hash(password),
                        false,
                        null,
                        null,
                        null,
                        now,
                        now,
                        now);
        if (!administrators.insertIfAbsent(administrator)) {
            throw new IllegalStateException(
                    "o administrador de teste já existe: " + normalizedEmail);
        }
        return new AdministratorFixture(administrator, normalizedEmail, password);
    }

    @Override
    public String toString() {
        return "AdministratorFixture[id=" + administrator.id() + "]";
    }
}
