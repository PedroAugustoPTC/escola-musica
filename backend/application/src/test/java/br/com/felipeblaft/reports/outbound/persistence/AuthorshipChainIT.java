package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cadeia da trilha sob concorrência (FR-040): 20 linhas de execução gravam ao mesmo tempo e a
 * cadeia sai contínua e verificável, com o {@code previous_hash} de cada linha igual ao {@code
 * record_hash} da anterior.
 */
class AuthorshipChainIT extends IntegrationBase {

    private static final int THREADS = 20;
    private static final int EVENTS_PER_THREAD = 5;

    @Autowired AuthorshipRecorderPort recorder;
    @Autowired AuditTrailQueryPort trail;

    private AuthorshipRecord event(int number) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                Role.ADMINISTRATOR,
                ActionOrigin.WEB,
                "ADMIN_SIGN_OUT",
                null,
                null,
                AuthorshipResult.SUCCESS,
                null,
                null,
                "req-" + number);
    }

    @Test
    void concurrentWritersProduceAContinuousVerifiableChain() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> running = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            int thread = t;
            running.add(
                    pool.submit(
                            () -> {
                                for (int i = 0; i < EVENTS_PER_THREAD; i++) {
                                    recorder.recordIndependent(event(thread * 10 + i));
                                }
                            }));
        }
        for (Future<?> future : running) {
            future.get();
        }
        pool.shutdown();

        List<AuditChain.Link> chain = trail.chainFrom(0, 1000);

        assertThat(chain).hasSize(THREADS * EVENTS_PER_THREAD);
        assertThat(chain.getFirst().previousHash()).isEqualTo(AuditChain.GENESIS_HASH);
        for (int i = 1; i < chain.size(); i++) {
            assertThat(chain.get(i).previousHash()).isEqualTo(chain.get(i - 1).recordHash());
        }
        assertThat(AuditChain.firstBroken(chain, AuditChain.GENESIS_HASH)).isEmpty();
    }
}
