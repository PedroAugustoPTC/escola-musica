package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BrazilianPhoneTest {

    private static void refuses(String text) {
        assertThatThrownBy(() -> BrazilianPhone.of(text))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_PHONE));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "11987654321",
                "(11) 98765-4321",
                "11 98765 4321",
                "(11)98765.4321",
                "  (11) 98765-4321  "
            })
    void mobileAcceptsMaskAndKeepsOnlyDigits(String text) {
        assertThat(BrazilianPhone.of(text).digits()).isEqualTo("11987654321");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1134567890", "(11) 3456-7890", "11 3456 7890"})
    void landlineHasTenDigits(String text) {
        assertThat(BrazilianPhone.of(text).digits()).isEqualTo("1134567890");
    }

    @Test
    void areaCodeRangesFromElevenToNinetyNine() {
        assertThat(BrazilianPhone.of("11987654321").digits()).startsWith("11");
        assertThat(BrazilianPhone.of("99987654321").digits()).startsWith("99");
        refuses("10987654321");
        refuses("0987654321");
        refuses("00987654321");
    }

    @Test
    void mobileMustStartWithNine() {
        refuses("11887654321");
        refuses("11787654321");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "   ",
                "119876543",
                "119876543210",
                "+5511987654321",
                "abc",
                "(11) 98765-43a1",
                "11 9876 5432 1 0"
            })
    void otherShapesAreRefused(String text) {
        refuses(text);
    }

    @Test
    void equalityByDigitsAndNoPersonalDataInText() {
        BrazilianPhone phone = BrazilianPhone.of("(11) 98765-4321");
        assertThat(phone).isEqualTo(BrazilianPhone.of("11987654321"));
        assertThat(phone).hasSameHashCodeAs(BrazilianPhone.of("11987654321"));
        assertThat(phone).isNotEqualTo(BrazilianPhone.of("11987654322"));
        assertThat(phone).isNotEqualTo("11987654321");
        assertThat(phone.toString()).doesNotContain("987").doesNotContain("11");
    }
}
