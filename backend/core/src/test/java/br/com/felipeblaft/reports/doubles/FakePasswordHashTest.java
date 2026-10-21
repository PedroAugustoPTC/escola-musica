package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Secret;
import org.junit.jupiter.api.Test;

class FakePasswordHashTest {

    private static final Secret PASSWORD = new Secret("random-test-password");

    private final FakePasswordHash hasher = new FakePasswordHash();

    @Test
    void hashNeverContainsThePlainPassword() {
        String hash = hasher.hash(PASSWORD);

        assertThat(hash).doesNotContain(PASSWORD.value());
    }

    @Test
    void samePasswordGetsDifferentHashesBecauseOfTheSalt() {
        String first = hasher.hash(PASSWORD);
        String second = hasher.hash(PASSWORD);

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.matches(PASSWORD, first)).isTrue();
        assertThat(hasher.matches(PASSWORD, second)).isTrue();
    }

    @Test
    void matchesOnlyTheRightPassword() {
        String hash = hasher.hash(PASSWORD);

        assertThat(hasher.matches(new Secret("another-password"), hash)).isFalse();
    }

    @Test
    void malformedHashNeverMatchesAndAlwaysNeedsRehash() {
        assertThat(hasher.matches(PASSWORD, "this-is-not-a-hash")).isFalse();
        assertThat(hasher.matches(PASSWORD, null)).isFalse();
        assertThat(hasher.needsRehash("this-is-not-a-hash")).isTrue();
        assertThat(hasher.needsRehash(null)).isTrue();
    }

    @Test
    void freshHashDoesNotNeedRehash() {
        assertThat(hasher.needsRehash(hasher.hash(PASSWORD))).isFalse();
    }

    @Test
    void raisingTheParametersMakesOldHashesNeedRehashButStillMatch() {
        String old = hasher.hash(PASSWORD);

        hasher.raiseParameters();

        assertThat(hasher.needsRehash(old)).isTrue();
        assertThat(hasher.matches(PASSWORD, old)).isTrue();
        assertThat(hasher.needsRehash(hasher.hash(PASSWORD))).isFalse();
    }

    @Test
    void decoyVerificationIsCounted() {
        assertThat(hasher.decoyVerifications()).isZero();

        hasher.verifyAgainstDecoy(PASSWORD);
        hasher.verifyAgainstDecoy(new Secret("other"));

        assertThat(hasher.decoyVerifications()).isEqualTo(2);
    }

    @Test
    void countsTheHashesProduced() {
        hasher.hash(PASSWORD);
        hasher.hash(PASSWORD);

        assertThat(hasher.hashesProduced()).isEqualTo(2);
    }
}
