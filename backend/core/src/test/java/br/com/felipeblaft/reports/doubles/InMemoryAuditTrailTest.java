package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryAuditTrailTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final AuthorshipService authorship = new AuthorshipService(trail, clock);

    private void append() {
        clock.advance(Duration.ofSeconds(1));
        authorship.record(
                ActorFixtures.administratorWeb(),
                Operation.STUDENT_REGISTER,
                AuthorshipTargetType.STUDENT,
                UUID.randomUUID(),
                "req-1");
    }

    @Test
    void chainsEveryEventToThePreviousOne() {
        append();
        append();
        append();

        var links = trail.chainFrom(0, 10);

        assertThat(links).hasSize(3);
        assertThat(links.getFirst().previousHash()).isEqualTo(AuditChain.GENESIS_HASH);
        assertThat(links.get(1).previousHash()).isEqualTo(links.getFirst().recordHash());
        assertThat(AuditChain.firstBroken(links, AuditChain.GENESIS_HASH)).isEmpty();
    }

    @Test
    void tamperingBreaksTheChainAtThatRow() {
        append();
        append();
        append();
        trail.tamper(2);

        assertThat(AuditChain.firstBroken(trail.chainFrom(0, 10), AuditChain.GENESIS_HASH))
                .hasValue(2L);
    }

    @Test
    void purgeKeepsAnAnchorSoTheRestStillVerifies() {
        append();
        append();
        append();
        int removed = trail.purgeOlderThan(clock.now());

        var anchor = trail.latestAnchor().orElseThrow();
        assertThat(removed).isEqualTo(2);
        assertThat(
                        AuditChain.firstBroken(
                                trail.chainFrom(anchor.purgedUntilSequence(), 10),
                                anchor.lastRecordHash()))
                .isEmpty();
    }
}
