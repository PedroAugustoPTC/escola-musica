package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AttemptPolicyTest {

    @Test
    void consecutivePolicyHasNoWindow() {
        AttemptPolicy policy = AttemptPolicy.consecutive(5, Duration.ofMinutes(15));
        assertThat(policy.maxAttempts()).isEqualTo(5);
        assertThat(policy.lockDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.hasWindow()).isFalse();
    }

    @Test
    void windowedPolicyKeepsTheWindow() {
        AttemptPolicy policy =
                AttemptPolicy.windowed(30, Duration.ofMinutes(60), Duration.ofMinutes(60));
        assertThat(policy.hasWindow()).isTrue();
        assertThat(policy.window()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void rejectsInvalidPolicies() {
        assertThatThrownBy(() -> AttemptPolicy.consecutive(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttemptPolicy.consecutive(5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttemptPolicy.consecutive(5, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttemptPolicy.windowed(5, Duration.ofMinutes(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                AttemptPolicy.windowed(
                                        5, Duration.ofMinutes(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttemptPolicy.windowed(5, Duration.ofMinutes(1), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttemptPolicy(5, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void scopesAreTheFiveOfTheDataModel() {
        assertThat(AttemptScope.values())
                .containsExactly(
                        AttemptScope.TEACHER_CODE,
                        AttemptScope.ADMIN_SIGN_IN,
                        AttemptScope.ADMIN_ACTIVATION,
                        AttemptScope.TEACHER_CPF_PROBE,
                        AttemptScope.TEACHER_CPF_LOOKUP);
    }
}
