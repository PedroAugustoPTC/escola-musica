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
        assertThat(comparator.equal("segredo", "segredo")).isTrue();
        assertThat(comparator.equal("segredo", "segredx")).isFalse();
        assertThat(comparator.equal("", "segredo")).isFalse();
    }

    @Test
    void differentSizesAndPrefixesAreNotAccepted() {
        assertThat(comparator.equal("segredo", "segredo-longo")).isFalse();
        assertThat(comparator.equal("segredo-longo", "segredo")).isFalse();
        assertThat(comparator.equal("s", "segredo")).isFalse();
    }

    @Test
    void nullAndEmptyNeverAuthenticate() {
        assertThat(comparator.equal(null, null)).isFalse();
        assertThat(comparator.equal("", "")).isFalse();
        assertThat(comparator.equal(null, "")).isFalse();
        assertThat(comparator.equal("qualquer", null)).isFalse();
        assertThat(comparator.equal("qualquer", "")).isFalse();
        assertThat(comparator.equal(null, "segredo")).isFalse();
    }

    @Test
    void baitIsNotReplayedByKnownInput() {
        for (String attempt : List.of("", "isca", "isca:", "0".repeat(64), "null", "nenhum")) {
            assertThat(comparator.equal(attempt, null)).isFalse();
        }
    }

    @Test
    void eitherOfTheTwoTokensAuthenticates() {
        assertThat(comparator.containsAny("atual", List.of("atual", "proximo"))).isTrue();
        assertThat(comparator.containsAny("proximo", List.of("atual", "proximo"))).isTrue();
        assertThat(comparator.containsAny("outro", List.of("atual", "proximo"))).isFalse();
    }

    @Test
    void alwaysEvaluatesAllConfiguredOnesEvenAfterAHit() {
        assertThat(comparator.containsAny("atual", List.of("atual", "proximo"))).isTrue();
        assertThat(assessments.get()).isEqualTo(2);
        assessments.set(0);
        assertThat(comparator.containsAny("errado", List.of("atual", "proximo"))).isFalse();
        assertThat(assessments.get()).isEqualTo(2);
        assessments.set(0);
        // token ausente também conta como avaliado (contra a isca)
        assertThat(comparator.containsAny("atual", Arrays.asList("atual", null))).isTrue();
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
