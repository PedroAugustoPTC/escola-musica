package br.com.felipeblaft.reports.inbound.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.ApplicationProperties.Bootstrap;
import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Recuperação do acesso (FR-034b) com o banco real: o mantenedor remove as linhas de {@code
 * administrator}, reinicia, e o primeiro administrador volta com senha provisória. As sessões somem
 * em cascata e o {@code authorship_record} é preservado.
 */
class AdministratorRecoveryDatabaseIT extends IntegrationBase {

    private static final String FIRST_PASSWORD = "fictitious-password-for-tests-1";
    private static final String SECOND_PASSWORD = "fictitious-password-for-tests-2";

    @Autowired BootstrapFirstAdministratorUseCase useCase;

    private void startUp(String password) {
        new FirstAdministratorBootstrap(
                        useCase,
                        new Bootstrap("Fictitious Administrator", "admin@example.test", password),
                        new MessageCatalog())
                .bootstrap();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void removingTheAdministratorsAndRestartingRecreatesTheFirstOneKeepingTheAuthorship() {
        startUp(FIRST_PASSWORD);
        UUID first = jdbc.queryForObject("select id from administrator", UUID.class);
        Instant now = clock.now();
        jdbc.update(
                "insert into administrator_session"
                        + " (id, administrator_id, token_hash, created_at, expires_at)"
                        + " values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                first,
                "a".repeat(64),
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(7200)));
        assertThat(count("administrator_session")).isEqualTo(1);
        assertThat(count("authorship_record")).isEqualTo(1);

        // o mantenedor remove as linhas de administrator (a sessão cai em cascata)
        jdbc.update("delete from administrator");

        assertThat(count("administrator")).isZero();
        assertThat(count("administrator_session")).isZero();
        assertThat(count("authorship_record")).as("authorship is preserved").isEqualTo(1);

        startUp(SECOND_PASSWORD);

        UUID recreated = jdbc.queryForObject("select id from administrator", UUID.class);
        assertThat(recreated).isNotEqualTo(first);
        assertThat(
                        jdbc.queryForObject(
                                "select password_provisional from administrator", Boolean.class))
                .isTrue();
        assertThat(count("authorship_record")).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record"
                                        + " where target_id = ? and origin = 'SYSTEM_STARTUP'",
                                Integer.class,
                                first))
                .as("the removed administrator record keeps pointing to it")
                .isEqualTo(1);
    }

    @Test
    void startingAgainWithAnotherPasswordNeverChangesTheExistingAdministrator() {
        startUp(FIRST_PASSWORD);
        String hash = jdbc.queryForObject("select password_hash from administrator", String.class);

        startUp(SECOND_PASSWORD);

        assertThat(count("administrator")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from administrator", String.class))
                .isEqualTo(hash);
    }
}
