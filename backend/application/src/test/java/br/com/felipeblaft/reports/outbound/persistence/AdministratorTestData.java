package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Construtores de dados dos testes de persistência do administrador (valores fictícios). */
final class AdministratorTestData {

    static final Instant NOW = Instant.parse("2026-09-21T13:00:00.123456Z");

    private AdministratorTestData() {}

    /** Administrador ativo e ativado, com hash de senha fictício. */
    static Administrator active(String name, String email) {
        return new Administrator(
                GeneratorId.create(),
                name,
                email,
                true,
                "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0$aGFzaGhhc2hoYXNoaGFzaGhhc2g",
                false,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW);
    }

    /** Administrador ativo e pendente de ativação, com o hash do código de ativação dado. */
    static Administrator pending(String name, String email, String activationCodeHash) {
        return new Administrator(
                GeneratorId.create(),
                name,
                email,
                true,
                null,
                false,
                null,
                activationCodeHash,
                NOW.plus(Duration.ofDays(7)),
                null,
                NOW,
                NOW);
    }

    static Administrator inactive(String name, String email) {
        return new Administrator(
                GeneratorId.create(),
                name,
                email,
                false,
                null,
                false,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW);
    }

    static AdministratorSession session(UUID administratorId, String tokenHash, Instant expiresAt) {
        return new AdministratorSession(
                GeneratorId.create(), administratorId, tokenHash, NOW, expiresAt, null, null);
    }

    /** Hash de token de 64 caracteres hexadecimais, derivado de um número. */
    static String tokenHash(int n) {
        return String.format("%064x", n);
    }

    static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
