package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FixedActivationCodeGenerator;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ActivationCodeIssuanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final FixedActivationCodeGenerator generator =
            new FixedActivationCodeGenerator("KQMXPTRWHNCV");
    private final FakeDataProtection protection = new FakeDataProtection();
    private final ActivationCodeIssuanceService service =
            new ActivationCodeIssuanceService(generator, protection);

    @Test
    void issuesTheGeneratedCodeItsHmacAndSevenDaysOfValidity() {
        ActivationCodeIssuanceService.Issued issued = service.issue(NOW);

        assertThat(issued.code().value()).isEqualTo("KQMXPTRWHNCV");
        assertThat(issued.hash()).isEqualTo(protection.hashActivationCode(issued.code()));
        assertThat(issued.hash()).doesNotContain("***").isNotEqualTo(issued.code().value());
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void everyIssueAsksTheGeneratorForANewCode() {
        ActivationCodeIssuanceService.Issued first = service.issue(NOW);
        ActivationCodeIssuanceService.Issued second = service.issue(NOW);

        assertThat(second.code()).isNotEqualTo(first.code());
        assertThat(second.hash()).isNotEqualTo(first.hash());
    }

    @Test
    void theIssuedValueDoesNotRevealTheCode() {
        assertThat(service.issue(NOW).toString()).doesNotContain("KQMX");
    }

    @Test
    void requiresItsCollaborators() {
        assertThatThrownBy(() -> new ActivationCodeIssuanceService(null, protection))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActivationCodeIssuanceService(generator, null))
                .isInstanceOf(NullPointerException.class);
    }
}
