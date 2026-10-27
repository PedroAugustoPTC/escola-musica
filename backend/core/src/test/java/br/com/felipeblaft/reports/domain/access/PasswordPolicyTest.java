package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static final String EMAIL = "ana.souza@school.test";
    private static final String NAME = "Ana Clara Souza";

    private static Secret secret(String value) {
        return new Secret(value);
    }

    private static void refusedWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(code);
    }

    @Test
    void acceptsTheMinimumOfTwelveCodePoints() {
        Secret result = PasswordPolicy.validate(secret("abcdefghijkl"), EMAIL, NAME);

        assertThat(result.value()).isEqualTo("abcdefghijkl");
    }

    @Test
    void refusesElevenCodePoints() {
        refusedWith(
                () -> PasswordPolicy.validate(secret("abcdefghijk"), EMAIL, NAME),
                ErrorCode.PASSWORD_LENGTH);
    }

    @Test
    void acceptsTheMaximumOfOneHundredTwentyEightCodePoints() {
        String password = "a".repeat(128);

        assertThat(PasswordPolicy.validate(secret(password), EMAIL, NAME).value())
                .isEqualTo(password);
    }

    @Test
    void refusesMoreThanOneHundredTwentyEightCodePoints() {
        refusedWith(
                () -> PasswordPolicy.validate(secret("a".repeat(129)), EMAIL, NAME),
                ErrorCode.PASSWORD_LENGTH);
    }

    @Test
    void countsCodePointsAndNotUtf16Units() {
        String twelveEmojis = "😀".repeat(12);
        String sixEmojis = "😀".repeat(6);

        assertThat(PasswordPolicy.validate(secret(twelveEmojis), EMAIL, NAME).value())
                .isEqualTo(twelveEmojis);
        refusedWith(
                () -> PasswordPolicy.validate(secret(sixEmojis), EMAIL, NAME),
                ErrorCode.PASSWORD_LENGTH);
    }

    @Test
    void countsAfterNfcNormalization() {
        // 12 pontos de código decompostos viram 10 depois da NFC (dois acentos combinantes)
        String decomposed = "cafécaféca";
        assertThat(decomposed.codePointCount(0, decomposed.length())).isEqualTo(12);

        refusedWith(
                () -> PasswordPolicy.validate(secret(decomposed), EMAIL, NAME),
                ErrorCode.PASSWORD_LENGTH);
    }

    @Test
    void returnsThePasswordInNfc() {
        String decomposed = "senha-café-ok";

        Secret result = PasswordPolicy.validate(secret(decomposed), EMAIL, NAME);

        assertThat(result.value()).isEqualTo("senha-café-ok");
    }

    @Test
    void hasNoCompositionRule() {
        assertThat(PasswordPolicy.validate(secret("aaaaaaaaaaaa"), EMAIL, NAME).value())
                .isEqualTo("aaaaaaaaaaaa");
        assertThat(PasswordPolicy.validate(secret("123456789012"), EMAIL, NAME).value())
                .isEqualTo("123456789012");
        assertThat(PasswordPolicy.validate(secret("            "), EMAIL, NAME).value())
                .hasSize(12);
    }

    @Test
    void refusesAPasswordEqualToTheEmailIgnoringCase() {
        refusedWith(
                () -> PasswordPolicy.validate(secret("ANA.SOUZA@school.test"), EMAIL, NAME),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);
    }

    @Test
    void refusesAPasswordEqualToTheNameIgnoringCase() {
        refusedWith(
                () -> PasswordPolicy.validate(secret("ana clara souza"), EMAIL, NAME),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);
    }

    @Test
    void comparesTheNameAfterNormalization() {
        refusedWith(
                () ->
                        PasswordPolicy.validate(
                                secret("José da Silva Prado"), EMAIL, "José da Silva Prado"),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);
    }

    @Test
    void ignoresSpacesAtTheEdgesWhenComparingWithTheIdentity() {
        refusedWith(
                () -> PasswordPolicy.validate(secret(" ana.souza@school.test "), EMAIL, NAME),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);
        refusedWith(
                () -> PasswordPolicy.validate(secret("Ana Clara Souza  "), EMAIL, "  " + NAME),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);
    }

    @Test
    void acceptsAPasswordThatOnlyContainsTheIdentity() {
        assertThat(PasswordPolicy.validate(secret("Ana Clara Souza 2026!"), EMAIL, NAME).value())
                .isEqualTo("Ana Clara Souza 2026!");
    }

    @Test
    void lengthIsCheckedBeforeIdentity() {
        refusedWith(
                () -> PasswordPolicy.validate(secret("a@b.co"), "a@b.co", "Ana"),
                ErrorCode.PASSWORD_LENGTH);
    }

    @Test
    void skipsTheIdentityChecksWhenEmailAndNameAreUnknown() {
        assertThat(PasswordPolicy.validate(secret(EMAIL), null, null).value()).isEqualTo(EMAIL);
    }

    @Test
    void nullPasswordIsALengthViolation() {
        refusedWith(() -> PasswordPolicy.validate(null, EMAIL, NAME), ErrorCode.PASSWORD_LENGTH);
    }

    @Test
    void acceptableLengthMatchesTheLimits() {
        assertThat(PasswordPolicy.acceptableLength(secret("a".repeat(12)))).isTrue();
        assertThat(PasswordPolicy.acceptableLength(secret("a".repeat(128)))).isTrue();
        assertThat(PasswordPolicy.acceptableLength(secret("a".repeat(11)))).isFalse();
        assertThat(PasswordPolicy.acceptableLength(secret("a".repeat(129)))).isFalse();
        assertThat(PasswordPolicy.acceptableLength(null)).isFalse();
    }

    @Test
    void theRefusalNeverRepeatsThePassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate(secret("short-pw"), EMAIL, NAME))
                .satisfies(
                        error -> {
                            assertThat(error.getMessage()).doesNotContain("short-pw");
                            assertThat(((DomainException) error).details().toString())
                                    .doesNotContain("short-pw");
                        });
    }
}
