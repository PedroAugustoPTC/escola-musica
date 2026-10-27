package br.com.felipeblaft.reports.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Secret;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/**
 * Argon2id (feature 005, R-04, FR-004): parâmetros configuráveis e gravados no próprio hash, sal
 * único por senha, senha de até 128 caracteres sem truncar, hash "isca" para e-mail desconhecido e
 * detecção de necessidade de novo hash quando o custo configurado sobe.
 */
class Argon2PasswordHashAdapterTest {

    private static final int MEMORY = 19456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordHashAdapter adapter =
            Argon2PasswordHashAdapter.withParameters(MEMORY, ITERATIONS);

    @Test
    void hashIsArgon2idWithConfiguredParametersInside() {
        String hash = adapter.hash(new Secret("long-test-password-1"));

        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hash).doesNotContain("long-test-password-1");
        assertThat(hash.length()).isLessThanOrEqualTo(255);
    }

    @Test
    void sameSecretGetsADifferentSaltEachTime() {
        Secret password = new Secret("long-test-password-1");

        String first = adapter.hash(password);
        String second = adapter.hash(password);

        assertThat(first).isNotEqualTo(second);
        assertThat(adapter.matches(password, first)).isTrue();
        assertThat(adapter.matches(password, second)).isTrue();
    }

    @Test
    void rightPasswordMatchesAndWrongOneDoesNot() {
        String hash = adapter.hash(new Secret("long-test-password-1"));

        assertThat(adapter.matches(new Secret("long-test-password-1"), hash)).isTrue();
        assertThat(adapter.matches(new Secret("long-test-password-2"), hash)).isFalse();
        assertThat(adapter.matches(new Secret("LONG-TEST-PASSWORD-1"), hash)).isFalse();
        assertThat(adapter.matches(new Secret(""), hash)).isFalse();
    }

    @Test
    void passwordOf128CharactersIsNeverTruncated() {
        String base = "a".repeat(127);
        String hash = adapter.hash(new Secret(base + "b"));

        assertThat(adapter.matches(new Secret(base + "b"), hash)).isTrue();
        // os 128 caracteres contam inteiros: trocar só o último muda o resultado (sem truncar em
        // 72)
        assertThat(adapter.matches(new Secret(base + "c"), hash)).isFalse();
        assertThat(adapter.matches(new Secret(base), hash)).isFalse();
    }

    @Test
    void unicodeFormsOfTheSamePasswordAreEquivalent() {
        String composed = Normalizer.normalize("secure-pass-áéíõü-ç", Normalizer.Form.NFC);
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo(composed);

        String hash = adapter.hash(new Secret(composed));

        assertThat(adapter.matches(new Secret(decomposed), hash)).isTrue();
    }

    @Test
    void malformedOrAbsentHashNeverMatchesAndNeverThrows() {
        Secret password = new Secret("long-test-password-1");

        assertThat(adapter.matches(password, null)).isFalse();
        assertThat(adapter.matches(password, "")).isFalse();
        assertThat(adapter.matches(password, "not-a-hash")).isFalse();
        assertThat(adapter.matches(password, "$argon2id$v=19$m=19456,t=2,p=1$bad$bad")).isFalse();
        assertThat(
                        adapter.matches(
                                password,
                                "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234"))
                .isFalse();
    }

    @Test
    void hashWithOldParametersStillVerifiesAfterTheCostChanges() {
        Secret password = new Secret("long-test-password-1");
        String oldHash = Argon2PasswordHashAdapter.withParameters(8192, 1).hash(password);

        assertThat(oldHash).contains("m=8192,t=1,p=1");
        assertThat(adapter.matches(password, oldHash)).isTrue();
    }

    @Test
    void needsRehashOnlyWhenTheStoredCostIsBelowTheCurrentOne() {
        Secret password = new Secret("long-test-password-1");
        String current = adapter.hash(password);
        String lowerMemory =
                Argon2PasswordHashAdapter.withParameters(8192, ITERATIONS).hash(password);
        String lowerIterations = Argon2PasswordHashAdapter.withParameters(MEMORY, 1).hash(password);
        String stronger = Argon2PasswordHashAdapter.withParameters(32768, 3).hash(password);

        assertThat(adapter.needsRehash(current)).isFalse();
        assertThat(adapter.needsRehash(lowerMemory)).isTrue();
        assertThat(adapter.needsRehash(lowerIterations)).isTrue();
        assertThat(adapter.needsRehash(stronger)).isFalse();
    }

    @Test
    void needsRehashForUnreadableOrDifferentAlgorithmHashes() {
        assertThat(adapter.needsRehash(null)).isTrue();
        assertThat(adapter.needsRehash("")).isTrue();
        assertThat(adapter.needsRehash("not-a-hash")).isTrue();
        assertThat(adapter.needsRehash("$argon2i$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0$aGFzaA"))
                .isTrue();
        assertThat(
                        adapter.needsRehash(
                                "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234"))
                .isTrue();
    }

    @Test
    void decoyVerificationCostsAboutTheSameAsARealOneAndNeverThrows() {
        Secret password = new Secret("long-test-password-1");
        String hash = adapter.hash(password);
        // aquecimento (carga de classes e do JIT)
        adapter.matches(password, hash);
        adapter.verifyAgainstDecoy(password);

        long real = median(() -> adapter.matches(new Secret("other-password-123"), hash));
        long decoy = median(() -> adapter.verifyAgainstDecoy(new Secret("other-password-123")));

        // mesmo custo: a razão fica longe de zero (hash isca pulado) e longe do infinito
        assertThat((double) decoy / real).isBetween(0.4, 2.5);
        assertThat(decoy).isGreaterThan(5_000_000L); // pelo menos ~5 ms: houve Argon2 de verdade
    }

    @Test
    void decoyVerificationDoesNotDependOnThePasswordLength() {
        adapter.verifyAgainstDecoy(new Secret(""));
        adapter.verifyAgainstDecoy(new Secret("x".repeat(128)));
        adapter.verifyAgainstDecoy(new Secret("x".repeat(500)));
    }

    @Test
    void invalidCostParametersFailAtStartup() {
        assertThatThrownBy(() -> Argon2PasswordHashAdapter.withParameters(4, 2))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Argon2PasswordHashAdapter.withParameters(19456, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    private static long median(Runnable action) {
        long[] times = new long[5];
        for (int i = 0; i < times.length; i++) {
            long start = System.nanoTime();
            action.run();
            times[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(times);
        return times[times.length / 2];
    }
}
