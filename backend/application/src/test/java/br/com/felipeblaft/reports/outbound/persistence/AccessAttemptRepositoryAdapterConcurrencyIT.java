package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Contagem atômica das tentativas com Postgres real (FR-006, FR-041, R-05): seis tentativas
 * simultâneas erradas bloqueiam exatamente na quinta, a sexta não conta, e a senha correta que
 * chega depois da quinta é recusada. A migração V11 preserva as linhas existentes como {@code
 * TEACHER_CODE}.
 */
class AccessAttemptRepositoryAdapterConcurrencyIT extends IntegrationBase {

    private static final String ORIGIN = "198.51.100.23";
    private static final AttemptPolicy FIVE_THEN_LOCK =
            AttemptPolicy.consecutive(5, Duration.ofMinutes(15));

    @Autowired AccessAttemptRepository repository;

    private AccessAttemptsControlService service;

    @BeforeEach
    void buildService() {
        service = AttemptServiceFactory.create(repository, clock);
    }

    private List<AccessAttempt> runConcurrently(int threads, Callable<AccessAttempt> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<AccessAttempt>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return task.call();
                                }));
            }
            ready.await();
            go.countDown();
            List<AccessAttempt> results = new ArrayList<>();
            for (Future<AccessAttempt> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Integer rowCount() {
        return jdbc.queryForObject("select count(*) from access_attempt", Integer.class);
    }

    private Instant instantOf(String column) {
        return jdbc.queryForObject(
                        "select " + column + " from access_attempt", java.sql.Timestamp.class)
                .toInstant();
    }

    @Test
    void sixSimultaneousWrongAttemptsBlockExactlyAtTheFifthAndTheSixthDoesNotCount()
            throws Exception {
        List<AccessAttempt> results =
                runConcurrently(
                        6,
                        () ->
                                service.registerFailure(
                                        AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN));

        assertThat(results.stream().map(AccessAttempt::failedAttempts).sorted())
                .containsExactly(1, 2, 3, 4, 5, 5);
        assertThat(rowCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select failed_attempts from access_attempt", Integer.class))
                .isEqualTo(5);
        assertThat(instantOf("blocked_until"))
                .isBetween(
                        clock.now().plus(Duration.ofMinutes(15)).minusSeconds(1),
                        clock.now().plus(Duration.ofMinutes(15)).plusSeconds(1));
        assertThat(jdbc.queryForObject("select scope from access_attempt", String.class))
                .isEqualTo("ADMIN_SIGN_IN");
    }

    @Test
    void theCorrectPasswordAfterTheFifthFailureIsRefused() throws Exception {
        runConcurrently(
                5, () -> service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN));

        service.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);

        assertThatThrownBy(
                        () -> service.verifyLock(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED));
        assertThat(jdbc.queryForObject("select failed_attempts from access_attempt", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void successWithoutActiveLockZeroesTheCountAndTheLockEndZeroesItToo() {
        service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);
        service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);
        service.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);
        assertThat(jdbc.queryForObject("select failed_attempts from access_attempt", Integer.class))
                .isZero();

        for (int i = 0; i < 5; i++) {
            service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);
        }
        clock.advance(Duration.ofMinutes(15));
        AccessAttempt afterLock =
                service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);

        assertThat(afterLock.failedAttempts()).isEqualTo(1);
        assertThat(afterLock.blockedUntil()).isNull();
    }

    @Test
    void failureDuringTheLockNeitherCountsNorExtendsIt() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);
        }
        Instant before = instantOf("blocked_until");
        clock.advance(Duration.ofMinutes(5));

        service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);

        assertThat(jdbc.queryForObject("select failed_attempts from access_attempt", Integer.class))
                .isEqualTo(5);
        assertThat(instantOf("blocked_until")).isEqualTo(before);
    }

    @Test
    void eachScopeKeepsItsOwnRowForTheSameOrigin() {
        service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);
        service.registerFailure(AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ORIGIN);
        service.registerFailure(Channel.WEB, ORIGIN);

        assertThat(rowCount()).isEqualTo(3);
        assertThat(
                        jdbc.queryForList(
                                "select scope from access_attempt order by scope", String.class))
                .containsExactly("ADMIN_ACTIVATION", "ADMIN_SIGN_IN", "TEACHER_CODE");
    }

    @Test
    void windowStartIsPersistedAndTheCountRestartsWhenTheWindowPasses() {
        service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, "teacher-1");
        assertThat(instantOf("window_started_at")).isEqualTo(clock.now());

        clock.advance(Duration.ofMinutes(16));
        AccessAttempt restarted =
                service.registerFailure(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, "teacher-1");

        assertThat(restarted.failedAttempts()).isEqualTo(1);
        assertThat(restarted.windowStartedAt()).isEqualTo(clock.now());
    }

    @Test
    void searchAndLegacySaveRoundTripTheScope() {
        AccessAttempt created =
                repository.registerFailure(
                        AttemptScope.ADMIN_SIGN_IN,
                        Channel.WEB,
                        "hash-1",
                        clock.now(),
                        FIVE_THEN_LOCK);
        created.reset(clock.now());
        repository.save(created);

        assertThat(repository.search(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, "hash-1"))
                .hasValueSatisfying(
                        found -> {
                            assertThat(found.id()).isEqualTo(created.id());
                            assertThat(found.failedAttempts()).isZero();
                        });
        assertThat(repository.search(AttemptScope.TEACHER_CODE, Channel.WEB, "hash-1")).isEmpty();
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void migrationV11KeepsExistingRowsAsTeacherCodeAndSeparatesTheScopes() throws SQLException {
        MigrationTestSupport db = new MigrationTestSupport("migration_v11_attempts_test");
        db.dropSchema();
        try {
            db.flyway("10").migrate();
            db.execute(
                    "insert into access_attempt (id, channel, source_hash, failed_attempts,"
                            + " blocked_until, updated_at) values"
                            + " ('00000000-0000-0000-0000-000000000001', 'WEB', 'hash-existing', 3,"
                            + " null, now())");

            db.flyway(null).migrate();

            assertThat(
                            db.single(
                                    "select scope from access_attempt where source_hash ="
                                            + " 'hash-existing'"))
                    .isEqualTo("TEACHER_CODE");
            assertThat(db.single("select failed_attempts from access_attempt")).isEqualTo("3");
            assertThat(db.single("select window_started_at from access_attempt")).isNull();
            db.execute(
                    "insert into access_attempt (id, scope, channel, source_hash, failed_attempts,"
                            + " updated_at) values ('00000000-0000-0000-0000-000000000002',"
                            + " 'ADMIN_SIGN_IN', 'WEB', 'hash-existing', 1, now())");
            assertThat(db.single("select count(*) from access_attempt")).isEqualTo("2");
            assertThatThrownBy(
                            () ->
                                    db.execute(
                                            "insert into access_attempt (id, scope, channel,"
                                                    + " source_hash, failed_attempts, updated_at)"
                                                    + " values"
                                                    + " ('00000000-0000-0000-0000-000000000003',"
                                                    + " 'ADMIN_SIGN_IN', 'WEB', 'hash-existing', 1,"
                                                    + " now())"))
                    .isInstanceOf(SQLException.class);
        } finally {
            db.dropSchema();
        }
    }
}
