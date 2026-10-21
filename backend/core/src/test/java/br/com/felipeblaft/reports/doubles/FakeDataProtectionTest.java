package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import org.junit.jupiter.api.Test;

class FakeDataProtectionTest {

    private final FakeDataProtection protection = new FakeDataProtection();

    @Test
    void originHashByScopeIsDeterministicAndSeparatedByScope() {
        String signIn = protection.originHash(AttemptScope.ADMIN_SIGN_IN, "ana@school.test");

        assertThat(protection.originHash(AttemptScope.ADMIN_SIGN_IN, "ana@school.test"))
                .isEqualTo(signIn);
        assertThat(protection.originHash(AttemptScope.ADMIN_ACTIVATION, "ana@school.test"))
                .isNotEqualTo(signIn);
        assertThat(protection.originHash(AttemptScope.ADMIN_SIGN_IN, "other@school.test"))
                .isNotEqualTo(signIn);
    }

    @Test
    void originHashByScopeNeverCollidesWithTheChannelOne() {
        String byChannel = protection.originHash(Channel.WEB, "10.0.0.1");

        for (AttemptScope scope : AttemptScope.values()) {
            assertThat(protection.originHash(scope, "10.0.0.1")).isNotEqualTo(byChannel);
        }
    }

    @Test
    void activationCodeHashIsDeterministicAndDistinctFromTheAccessCodeHash() {
        assumeTrue(activationCodeImplemented(), "ActivationCode ainda é esqueleto (W-A, T035)");
        ActivationCode activation = ActivationCode.retry("KQMXPTRWHNCV").orElseThrow();

        String hash = protection.hashActivationCode(activation);

        assertThat(hash).isEqualTo(protection.hashActivationCode(activation));
        assertThat(hash).isNotEqualTo(protection.hashAccessCode(AccessCode.of("KQMXPTRW")));
        assertThat(hash).doesNotContain("***");
    }

    private static boolean activationCodeImplemented() {
        try {
            ActivationCode.retry("KQMXPTRWHNCV");
            return true;
        } catch (UnsupportedOperationException skeleton) {
            return false;
        }
    }
}
