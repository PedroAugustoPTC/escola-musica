package br.com.felipeblaft.reports.inbound.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** V5: comparação sobre digests, sem curto-circuito e fail-closed (verificação estrutural). */
class SecretComparatorTest {

    private final AtomicInteger assessments = new AtomicInteger();
    private final SecretComparator comparator = new SecretComparator(assessments::incrementAndGet);

    @Test
    void equalOnesAcceptAndDifferentOnesRefuse() {
        assertThat(comparator.equal("secret", "secret")).isTrue();
        assertThat(comparator.equal("secret", "segredx")).isFalse();
        assertThat(comparator.equal("", "secret")).isFalse();
    }

    @Test
    void differentSizesAndPrefixesAreNotAccepted() {
        assertThat(comparator.equal("secret", "long-secret")).isFalse();
        assertThat(comparator.equal("long-secret", "secret")).isFalse();
        assertThat(comparator.equal("s", "secret")).isFalse();
    }

    @Test
    void nullAndEmptyNeverAuthenticate() {
        assertThat(comparator.equal(null, null)).isFalse();
        assertThat(comparator.equal("", "")).isFalse();
        assertThat(comparator.equal(null, "")).isFalse();
        assertThat(comparator.equal("any", null)).isFalse();
        assertThat(comparator.equal("any", "")).isFalse();
        assertThat(comparator.equal(null, "secret")).isFalse();
    }

    @Test
    void baitIsNotReplayedByKnownInput() {
        for (String attempt : List.of("", "bait", "bait:", "0".repeat(64), "null", "none")) {
            assertThat(comparator.equal(attempt, null)).isFalse();
        }
    }

    @Test
    void eitherOfTheTwoTokensAuthenticates() {
        assertThat(comparator.containsAny("current", List.of("current", "next"))).isTrue();
        assertThat(comparator.containsAny("next", List.of("current", "next"))).isTrue();
        assertThat(comparator.containsAny("other", List.of("current", "next"))).isFalse();
    }

    @Test
    void alwaysEvaluatesAllConfiguredOnesEvenAfterAHit() {
        assertThat(comparator.containsAny("current", List.of("current", "next"))).isTrue();
        assertThat(assessments.get()).isEqualTo(2);
        assessments.set(0);
        assertThat(comparator.containsAny("wrong", List.of("current", "next"))).isFalse();
        assertThat(assessments.get()).isEqualTo(2);
        assessments.set(0);
        // token ausente também conta como avaliado (contra a isca)
        assertThat(comparator.containsAny("current", Arrays.asList("current", null))).isTrue();
        assertThat(assessments.get()).isEqualTo(2);
    }

    @Test
    void noConfiguredTokenAuthenticatesNothingNotEvenWhenReceivedIsEmpty() {
        assertThat(comparator.containsAny("", Arrays.asList(null, ""))).isFalse();
        assertThat(comparator.containsAny("x", List.of())).isFalse();
    }

    @Test
    void publicConstructorWorks() {
        assertThat(new SecretComparator().equal("a", "a")).isTrue();
    }
}
