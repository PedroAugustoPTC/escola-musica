package br.com.felipeblaft.reports.outbound.persistence;

import static br.com.felipeblaft.reports.outbound.persistence.AdministratorTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Administradores no PostgreSQL real (feature 005, migração V9): ida e volta, e-mail minúsculo e
 * único, inserção sem abortar a transação, primeiro administrador atômico, lista com busca, índice
 * único parcial do código de ativação pendente e remoção em cascata das sessões.
 */
class AdministratorRepositoryAdapterIT extends IntegrationBase {

    @Autowired AdministratorRepository administrators;

    @Test
    void roundTripKeepsEveryField() {
        Administrator pending =
                AdministratorTestData.pending(
                        "Marina Alves", "marina@example.test", "a".repeat(64));
        assertThat(administrators.insertIfAbsent(pending)).isTrue();

        Administrator read = administrators.searchById(pending.id()).orElseThrow();

        assertThat(read.id()).isEqualTo(pending.id());
        assertThat(read.name()).isEqualTo("Marina Alves");
        assertThat(read.email()).isEqualTo("marina@example.test");
        assertThat(read.active()).isTrue();
        assertThat(read.passwordHash()).isNull();
        assertThat(read.passwordProvisional()).isFalse();
        assertThat(read.activationCodeHash()).isEqualTo("a".repeat(64));
        assertThat(read.activationCodeExpiresAt())
                .isEqualTo(AdministratorTestData.micros(NOW.plus(Duration.ofDays(7))));
        assertThat(read.activatedAt()).isNull();
        assertThat(read.createdAt()).isEqualTo(AdministratorTestData.micros(NOW));
        assertThat(read.updatedAt()).isEqualTo(AdministratorTestData.micros(NOW));
    }

    @Test
    void provisionalPasswordFieldsAreStored() {
        Administrator first =
                new Administrator(
                        UUID.randomUUID(),
                        "Primeiro Admin",
                        "first@example.test",
                        true,
                        "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0$aGFzaA",
                        true,
                        NOW.plus(Duration.ofDays(7)),
                        null,
                        null,
                        NOW,
                        NOW,
                        NOW);

        assertThat(administrators.insertIfNoneExists(first)).isTrue();

        Administrator read = administrators.searchByEmail("first@example.test").orElseThrow();
        assertThat(read.passwordProvisional()).isTrue();
        assertThat(read.passwordProvisionalExpiresAt())
                .isEqualTo(AdministratorTestData.micros(NOW.plus(Duration.ofDays(7))));
        assertThat(read.passwordHash()).startsWith("$argon2id$");
        assertThat(read.activatedAt()).isEqualTo(AdministratorTestData.micros(NOW));
    }

    @Test
    void searchByEmailFindsTheLowercaseEmailAndUnknownIsEmpty() {
        Administrator admin = AdministratorTestData.active("Marina Alves", "marina@example.test");
        administrators.insertIfAbsent(admin);

        assertThat(administrators.searchByEmail("marina@example.test"))
                .map(Administrator::id)
                .contains(admin.id());
        assertThat(administrators.searchByEmail("nobody@example.test")).isEmpty();
        assertThat(administrators.searchById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void emailIsUniqueAndDuplicateInsertDoesNotAbortTheTransaction() {
        Administrator first = AdministratorTestData.active("Marina Alves", "marina@example.test");
        Administrator duplicate =
                AdministratorTestData.active("Outra Pessoa", "marina@example.test");

        assertThat(administrators.insertIfAbsent(first)).isTrue();
        assertThat(administrators.insertIfAbsent(duplicate)).isFalse();

        // a conexão segue utilizável: a duplicidade não deixou a transação abortada
        assertThat(administrators.count()).isEqualTo(1);
        assertThat(administrators.searchByEmail("marina@example.test").orElseThrow().name())
                .isEqualTo("Marina Alves");
    }

    @Test
    void databaseRejectsAnEmailThatIsNotLowercase() {
        Administrator upper = AdministratorTestData.active("Marina Alves", "Marina@Example.test");

        assertThatThrownBy(() -> administrators.insertIfAbsent(upper))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(administrators.count()).isZero();
    }

    @Test
    void emailUniquenessIsPerRoleSoItMayCoincideWithATeacherEmail() {
        jdbc.update(
                "insert into teacher (id, name, email, active, created_at, updated_at)"
                        + " values (?, 'Professora', 'shared@example.test', true, now(), now())",
                UUID.randomUUID());

        assertThat(
                        administrators.insertIfAbsent(
                                AdministratorTestData.active("Admin", "shared@example.test")))
                .isTrue();
    }

    @Test
    void insertIfNoneExistsInsertsOnlyTheFirstAdministrator() {
        Administrator first = AdministratorTestData.active("Primeiro", "first@example.test");
        Administrator second = AdministratorTestData.active("Segundo", "second@example.test");

        assertThat(administrators.insertIfNoneExists(first)).isTrue();
        assertThat(administrators.insertIfNoneExists(second)).isFalse();

        assertThat(administrators.count()).isEqualTo(1);
        assertThat(administrators.searchByEmail("second@example.test")).isEmpty();
    }

    @Test
    void insertIfNoneExistsIsAtomicUnderConcurrency() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Administrator admin =
                    AdministratorTestData.active("Admin " + i, "admin" + i + "@example.test");
            tasks.add(() -> administrators.insertIfNoneExists(admin));
        }

        List<Boolean> results = ConcurrentRunner.runTogether(tasks);

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(administrators.count()).isEqualTo(1);
    }

    @Test
    void insertIfAbsentWithTheSameEmailInParallelInsertsExactlyOne() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Administrator admin = AdministratorTestData.active("Admin " + i, "same@example.test");
            tasks.add(() -> administrators.insertIfAbsent(admin));
        }

        List<Boolean> results = ConcurrentRunner.runTogether(tasks);

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(administrators.count()).isEqualTo(1);
    }

    @Test
    void saveUpdatesTheExistingAdministrator() {
        Administrator admin =
                AdministratorTestData.pending("Marina", "marina@example.test", "b".repeat(64));
        administrators.insertIfAbsent(admin);

        administrators.save(
                new Administrator(
                        admin.id(),
                        "Marina Alves",
                        admin.email(),
                        true,
                        "$argon2id$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0$aGFzaA",
                        false,
                        null,
                        null,
                        null,
                        NOW.plusSeconds(60),
                        admin.createdAt(),
                        NOW.plusSeconds(60)));

        Administrator read = administrators.searchById(admin.id()).orElseThrow();
        assertThat(read.name()).isEqualTo("Marina Alves");
        assertThat(read.passwordHash()).startsWith("$argon2id$");
        assertThat(read.activationCodeHash()).isNull();
        assertThat(read.activationCodeExpiresAt()).isNull();
        assertThat(read.activatedAt()).isEqualTo(AdministratorTestData.micros(NOW.plusSeconds(60)));
        assertThat(administrators.count()).isEqualTo(1);
    }

    @Test
    void listIsAlphabeticalAndSearchesByNameOrEmailWithoutAccentsOrCase() {
        administrators.insertIfAbsent(
                AdministratorTestData.active("Zélia Prado", "zelia@example.test"));
        administrators.insertIfAbsent(
                AdministratorTestData.active("Ana Souza", "ana@example.test"));
        administrators.insertIfAbsent(
                AdministratorTestData.active("Álvaro Lima", "alvaro.lima@school.example"));
        administrators.insertIfAbsent(
                AdministratorTestData.inactive("Bruno Reis", "bruno@example.test"));

        PageResult<Administrator> all = administrators.list(null, null, PageRequest.first());
        assertThat(all.items())
                .extracting(Administrator::name)
                .containsExactly("Álvaro Lima", "Ana Souza", "Bruno Reis", "Zélia Prado");
        assertThat(all.total()).isEqualTo(4);

        assertThat(administrators.list("zelia", null, PageRequest.first()).items())
                .extracting(Administrator::name)
                .containsExactly("Zélia Prado");
        assertThat(administrators.list("ALVARO", null, PageRequest.first()).items())
                .extracting(Administrator::name)
                .containsExactly("Álvaro Lima");
        assertThat(administrators.list("@school.example", null, PageRequest.first()).items())
                .extracting(Administrator::name)
                .containsExactly("Álvaro Lima");
        assertThat(administrators.list("nobody", null, PageRequest.first()).items()).isEmpty();
        assertThat(administrators.list("   ", null, PageRequest.first()).total()).isEqualTo(4);
    }

    @Test
    void listFiltersBySituationAndPaginates() {
        for (int i = 0; i < 5; i++) {
            administrators.insertIfAbsent(
                    AdministratorTestData.active("Admin " + i, "active" + i + "@example.test"));
        }
        administrators.insertIfAbsent(
                AdministratorTestData.inactive("Inativo", "off@example.test"));

        assertThat(administrators.list(null, true, PageRequest.first()).total()).isEqualTo(5);
        assertThat(administrators.list(null, false, PageRequest.first()).items())
                .extracting(Administrator::name)
                .containsExactly("Inativo");

        PageResult<Administrator> second = administrators.list(null, true, new PageRequest(1, 2));
        assertThat(second.items())
                .extracting(Administrator::name)
                .containsExactly("Admin 2", "Admin 3");
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.total()).isEqualTo(5);
        assertThat(administrators.list(null, true, new PageRequest(9, 2)).items()).isEmpty();
    }

    @Test
    void pendingActivationCodeHashIsUniqueButManyAdministratorsMayHaveNone() {
        administrators.insertIfAbsent(
                AdministratorTestData.pending("Um", "one@example.test", "c".repeat(64)));
        administrators.insertIfAbsent(AdministratorTestData.active("Dois", "two@example.test"));
        administrators.insertIfAbsent(AdministratorTestData.active("Tres", "three@example.test"));

        Administrator clash =
                AdministratorTestData.pending("Quatro", "four@example.test", "c".repeat(64));
        assertThatThrownBy(() -> administrators.insertIfAbsent(clash))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from administrator where activation_code_hash is null",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void secretsAreOnlyStoredAsHashColumns() {
        administrators.insertIfAbsent(
                AdministratorTestData.pending("Um", "one@example.test", "d".repeat(64)));

        List<String> columns =
                jdbc.queryForList(
                        "select column_name from information_schema.columns"
                                + " where table_name = 'administrator'",
                        String.class);

        assertThat(columns)
                .contains(
                        "password_hash", "activation_code_hash", "password_provisional_expires_at")
                .doesNotContain("password", "activation_code", "cpf");
    }

    @Test
    void removingAdministratorsCascadesToSessionsButKeepsTheAuthorshipRecords() {
        Administrator admin = AdministratorTestData.active("Marina", "marina@example.test");
        administrators.insertIfAbsent(admin);
        jdbc.update(
                "insert into administrator_session (id, administrator_id, token_hash, created_at,"
                        + " expires_at) values (?, ?, ?, now(), now() + interval '2 hours')",
                UUID.randomUUID(),
                admin.id(),
                AdministratorTestData.tokenHash(1));
        boolean hasAuthorship =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "select to_regclass('authorship_record') is not null",
                                Boolean.class));
        if (hasAuthorship) {
            jdbc.update(
                    "insert into authorship_record (id, occurred_at, author_id, author_role, origin,"
                            + " operation, result, correlation_id) values (?, now(), ?,"
                            + " 'ADMINISTRATOR', 'WEB', 'ADMIN_SIGN_IN', 'SUCCESS', 'req-c1')",
                    UUID.randomUUID(),
                    admin.id());
        }

        jdbc.update("delete from administrator");

        assertThat(jdbc.queryForObject("select count(*) from administrator_session", Integer.class))
                .isZero();
        Assumptions.assumeTrue(
                hasAuthorship,
                "authorship_record chega com a V10 (fluxo W-F): sem ela só a cascata foi verificada");
        assertThat(jdbc.queryForObject("select count(*) from authorship_record", Integer.class))
                .isEqualTo(1);
    }
}
