package br.com.felipeblaft.reports.inbound.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.ApplicationProperties.Bootstrap;
import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * As garantias da subida que dependem do banco (contracts/initial-load.md §4), com o caso de uso e
 * os adaptadores reais: duas instâncias criam no máximo um administrador, a senha fica só como hash
 * Argon2id e a autoria registra o ator de sistema da inicialização, sem a senha nem o e-mail.
 */
class FirstAdministratorBootstrapDatabaseIT extends IntegrationBase {

    private static final String PASSWORD = "fictitious-password-for-tests-1";
    private static final String NAME = "Fictitious Administrator";

    @Autowired BootstrapFirstAdministratorUseCase useCase;

    private FirstAdministratorBootstrap instance(int number) {
        return new FirstAdministratorBootstrap(
                useCase,
                new Bootstrap(NAME + " " + number, "admin" + number + "@example.test", PASSWORD),
                new MessageCatalog());
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void twoInstancesStartingTogetherCreateAtMostOneAdministrator() throws Exception {
        for (int round = 0; round < 3; round++) {
            truncateAll();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> runs = new ArrayList<>();
            for (int number = 1; number <= 2; number++) {
                FirstAdministratorBootstrap bootstrap = instance(number);
                runs.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    bootstrap.bootstrap();
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
            pool.shutdown();

            assertThat(count("administrator")).as("round %d", round).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from authorship_record"
                                            + " where operation = 'FIRST_ADMINISTRATOR_BOOTSTRAP'",
                                    Integer.class))
                    .as("round %d", round)
                    .isEqualTo(1);
        }
    }

    @Test
    void thePasswordIsStoredOnlyAsAnArgon2idHashAndIsProvisional() {
        instance(1).bootstrap();

        var row =
                jdbc.queryForMap(
                        "select password_hash, password_provisional, active,"
                                + " extract(epoch from (password_provisional_expires_at"
                                + " - created_at)) as validity from administrator");
        assertThat((String) row.get("password_hash"))
                .startsWith("$argon2id$")
                .doesNotContain(PASSWORD);
        assertThat(row.get("password_provisional")).isEqualTo(true);
        assertThat(row.get("active")).isEqualTo(true);
        assertThat(((Number) row.get("validity")).longValue()).isEqualTo(7L * 24 * 3600);
    }

    @Test
    void authorshipRecordsTheStartupActorWithoutThePasswordOrTheEmail() {
        instance(1).bootstrap();

        UUID administrator = jdbc.queryForObject("select id from administrator", UUID.class);
        var row =
                jdbc.queryForMap(
                        "select author_id, author_role, origin, operation, target_type, target_id,"
                                + " result from authorship_record");
        assertThat(row.get("author_id")).isNull();
        assertThat(row.get("author_role")).isNull();
        assertThat(row.get("origin")).isEqualTo("SYSTEM_STARTUP");
        assertThat(row.get("operation")).isEqualTo("FIRST_ADMINISTRATOR_BOOTSTRAP");
        assertThat(row.get("target_type")).isEqualTo("ADMINISTRATOR");
        assertThat(row.get("target_id")).isEqualTo(administrator);
        assertThat(row.get("result")).isEqualTo("SUCCESS");

        String everything =
                jdbc.queryForObject(
                        "select string_agg(t::text, ' ') from authorship_record t", String.class);
        assertThat(everything).doesNotContain(PASSWORD, "admin1@example.test");
    }

    @Test
    void anExistingAdministratorMakesTheNextStartupIgnoreEvenOtherValues() {
        instance(1).bootstrap();
        String hash = jdbc.queryForObject("select password_hash from administrator", String.class);

        instance(2).bootstrap();

        assertThat(count("administrator")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from administrator", String.class))
                .isEqualTo(hash);
        assertThat(count("authorship_record")).isEqualTo(1);
    }
}
