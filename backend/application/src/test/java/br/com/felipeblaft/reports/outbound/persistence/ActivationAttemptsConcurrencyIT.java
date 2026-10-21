package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Escopo {@code ADMIN_ACTIVATION} com Postgres real (FR-006, US7): a contagem das ativações é
 * atômica e separada da contagem da entrada com senha, mesmo para a mesma origem.
 */
class ActivationAttemptsConcurrencyIT extends IntegrationBase {

    private static final String ORIGIN = "203.0.113.77";

    @Autowired AccessAttemptRepository repository;

    private AccessAttemptsControlService service;

    @BeforeEach
    void buildService() {
        service = AttemptServiceFactory.create(repository, clock);
    }

    private List<AccessAttempt> failActivationConcurrently(int threads) throws Exception {
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
                                    return service.registerFailure(
                                            AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ORIGIN);
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

    @Test
    void sixSimultaneousWrongActivationsBlockAtTheFifthAndLeaveSignInFree() throws Exception {
        List<AccessAttempt> results = failActivationConcurrently(6);

        assertThat(results.stream().map(AccessAttempt::failedAttempts).sorted())
                .containsExactly(1, 2, 3, 4, 5, 5);
        assertThat(
                        jdbc.queryForObject(
                                "select failed_attempts from access_attempt where scope ="
                                        + " 'ADMIN_ACTIVATION'",
                                Integer.class))
                .isEqualTo(5);
        assertThatThrownBy(
                        () ->
                                service.verifyLock(
                                        AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ORIGIN))
                .isInstanceOf(DomainException.class);
        assertThatCode(() -> service.verifyLock(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN))
                .doesNotThrowAnyException();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from access_attempt where scope = 'ADMIN_SIGN_IN'",
                                Integer.class))
                .isZero();
    }

    @Test
    void signInFailuresDoNotCountForTheActivation() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, ORIGIN);
        }

        AccessAttempt first =
                service.registerFailure(AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ORIGIN);

        assertThat(first.failedAttempts()).isEqualTo(1);
        assertThatCode(() -> service.verifyLock(AttemptScope.ADMIN_ACTIVATION, Channel.WEB, ORIGIN))
                .doesNotThrowAnyException();
    }
}
