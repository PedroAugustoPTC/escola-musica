package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GuardianTest {

    private static final PersonName NAME = PersonName.of("Maria Silva");

    @Test
    void holdsNamePhoneAndEmail() {
        Guardian guardian =
                new Guardian(
                        NAME,
                        BrazilianPhone.of("(11) 98765-4321"),
                        EmailAddress.of("maria@example.com"));
        assertThat(guardian.name().value()).isEqualTo("Maria Silva");
        assertThat(guardian.phone().digits()).isEqualTo("11987654321");
        assertThat(guardian.email().value()).isEqualTo("maria@example.com");
        assertThat(guardian.isComplete()).isTrue();
    }

    @Test
    void legacyGuardianKeepsOnlyTheName() {
        assertThat(new Guardian(NAME, null, null).isComplete()).isFalse();
        assertThat(new Guardian(NAME, BrazilianPhone.of("1134567890"), null).isComplete())
                .isFalse();
        assertThat(new Guardian(NAME, null, EmailAddress.of("maria@example.com")).isComplete())
                .isFalse();
    }

    @Test
    void nameIsRequired() {
        assertThatThrownBy(() -> new Guardian(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void textRevealsNoPersonalData() {
        Guardian guardian =
                new Guardian(
                        NAME,
                        BrazilianPhone.of("11987654321"),
                        EmailAddress.of("maria@example.com"));
        assertThat(guardian.toString())
                .doesNotContain("Maria")
                .doesNotContain("987")
                .doesNotContain("example");
    }
}
