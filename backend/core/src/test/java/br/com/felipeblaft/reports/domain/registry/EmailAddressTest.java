package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    private static void refuses(String text, ErrorCode expected) {
        assertThatThrownBy(() -> EmailAddress.of(text))
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    @Test
    void trimsAndLowercases() {
        assertThat(EmailAddress.of("  Maria.Silva@Example.COM ").value())
                .isEqualTo("maria.silva@example.com");
    }

    @Test
    void acceptsTheMaximumLengthAndRefusesOneMore() {
        String domain = "@exemplo.com";
        String at254 = "a".repeat(254 - domain.length()) + domain;
        assertThat(at254).hasSize(254);
        assertThat(EmailAddress.of(at254).value()).hasSize(254);
        refuses("a" + at254, ErrorCode.INVALID_EMAIL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "   ",
                "sem-arroba.com",
                "@exemplo.com",
                "maria@",
                "maria@exemplo",
                "maria@@exemplo.com",
                "ma ria@exemplo.com",
                "maria@exem plo.com"
            })
    void invalidFormatIsRefused(String text) {
        refuses(text, ErrorCode.INVALID_EMAIL);
    }

    @Test
    void controlCharactersAreRefusedAsInvalidText() {
        refuses("maria\u0000@exemplo.com", ErrorCode.INVALID_TEXT);
        refuses("maria@exe\tmplo.com", ErrorCode.INVALID_TEXT);
    }

    @Test
    void equalityByValueAndNoPersonalDataInText() {
        EmailAddress email = EmailAddress.of("maria@exemplo.com");
        assertThat(email).isEqualTo(EmailAddress.of(" MARIA@exemplo.com "));
        assertThat(email).hasSameHashCodeAs(EmailAddress.of("maria@exemplo.com"));
        assertThat(email).isNotEqualTo(EmailAddress.of("outra@exemplo.com"));
        assertThat(email).isNotEqualTo("maria@exemplo.com");
        assertThat(email.toString()).doesNotContain("maria").doesNotContain("exemplo");
    }
}
