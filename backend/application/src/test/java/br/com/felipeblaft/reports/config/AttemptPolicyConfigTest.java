package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * As políticas de CPF do professor vêm de {@code reports.access.cpf-probe.*} e {@code
 * reports.access.cpf-lookup.*}, e não dos padrões do núcleo.
 */
class AttemptPolicyConfigTest {

    @Test
    void cpfPoliciesComeFromTheConfiguredProperties() {
        ApplicationProperties.Access access =
                new ApplicationProperties.Access(
                        5,
                        15,
                        new ApplicationProperties.CpfProbe(6, 20, 25),
                        new ApplicationProperties.CpfLookup(40, 90, 120));

        Map<AttemptScope, AttemptPolicy> policies = UseCaseConfig.attemptPolicyOverrides(access);

        assertThat(policies)
                .containsOnlyKeys(AttemptScope.TEACHER_CPF_PROBE, AttemptScope.TEACHER_CPF_LOOKUP);
        assertThat(policies.get(AttemptScope.TEACHER_CPF_PROBE))
                .isEqualTo(
                        AttemptPolicy.windowed(6, Duration.ofMinutes(25), Duration.ofMinutes(20)));
        assertThat(policies.get(AttemptScope.TEACHER_CPF_LOOKUP))
                .isEqualTo(
                        AttemptPolicy.windowed(
                                40, Duration.ofMinutes(120), Duration.ofMinutes(90)));
    }
}
