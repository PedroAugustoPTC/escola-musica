package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdministratorIdentityTest {

    @Test
    void normalizesTheNameAndTheEmail() {
        AdministratorIdentity identity =
                AdministratorIdentity.of("  José Prado ", "  Jose@School.TEST ");

        assertThat(identity.name()).isEqualTo("José Prado");
        assertThat(identity.email()).isEqualTo("jose@school.test");
    }

    @Test
    void acceptsTheNameLimits() {
        assertThat(AdministratorIdentity.of("Al", "a@b.co").name()).isEqualTo("Al");
        assertThat(AdministratorIdentity.of("a".repeat(120), "a@b.co").name()).hasSize(120);
    }

    @Test
    void refusesANameOutsideTheLimits() {
        assertThat(AdministratorIdentity.violations("A", "a@b.co"))
                .containsExactly(
                        new AdministratorIdentity.Violation("name", ErrorCode.INVALID_NAME));
        assertThat(AdministratorIdentity.violations("a".repeat(121), "a@b.co"))
                .containsExactly(
                        new AdministratorIdentity.Violation("name", ErrorCode.INVALID_NAME));
        assertThat(AdministratorIdentity.violations("   ", "a@b.co"))
                .containsExactly(
                        new AdministratorIdentity.Violation("name", ErrorCode.INVALID_NAME));
        assertThat(AdministratorIdentity.violations(null, "a@b.co"))
                .containsExactly(
                        new AdministratorIdentity.Violation("name", ErrorCode.INVALID_NAME));
    }

    @Test
    void refusesControlCharactersInTheName() {
        assertThat(AdministratorIdentity.violations("Ana\u0007 Souza", "a@b.co"))
                .containsExactly(
                        new AdministratorIdentity.Violation("name", ErrorCode.INVALID_TEXT));
    }

    @Test
    void refusesAMalformedOrTooLongEmail() {
        for (String email :
                new String[] {null, "", "ana", "ana@", "@school.test", "a b@school.test", "a@b"}) {
            assertThat(AdministratorIdentity.violations("Ana Souza", email))
                    .as(String.valueOf(email))
                    .containsExactly(
                            new AdministratorIdentity.Violation("email", ErrorCode.INVALID_EMAIL));
        }
        String tooLong = "a".repeat(250) + "@b.co";
        assertThat(AdministratorIdentity.violations("Ana Souza", tooLong))
                .containsExactly(
                        new AdministratorIdentity.Violation("email", ErrorCode.INVALID_EMAIL));
    }

    @Test
    void accumulatesAllTheViolationsInOneRefusal() {
        assertThatThrownBy(() -> AdministratorIdentity.of("A", "invalid"))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> {
                            assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                            assertThat(error.details().get("fields"))
                                    .isEqualTo(
                                            List.of(
                                                    Map.of("field", "name", "code", "INVALID_NAME"),
                                                    Map.of(
                                                            "field",
                                                            "email",
                                                            "code",
                                                            "INVALID_EMAIL")));
                        });
    }

    @Test
    void theRefusalNeverRepeatsTheValues() {
        assertThatThrownBy(() -> AdministratorIdentity.of("X", "secret-text"))
                .satisfies(
                        error -> {
                            assertThat(error.getMessage()).doesNotContain("secret-text");
                            assertThat(((DomainException) error).details().toString())
                                    .doesNotContain("secret-text");
                        });
    }

    @Test
    void detailsOfAnEmptyListIsEmpty() {
        assertThat(AdministratorIdentity.details(List.of())).isEmpty();
    }

    @Test
    void toStringRevealsNothing() {
        assertThat(AdministratorIdentity.of("Ana Souza", "ana@school.test").toString())
                .doesNotContain("Ana", "ana@school.test");
    }
}
