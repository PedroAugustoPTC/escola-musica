package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActivationCodeTest {

    private static final String CODE = "KQMXPTRWHNCV";

    @Test
    void normalizesSpacesAndCase() {
        assertThat(ActivationCode.retry("  kqmxptrwhncv "))
                .map(ActivationCode::value)
                .contains(CODE);
    }

    @Test
    void acceptsTheGroupedForms() {
        assertThat(ActivationCode.retry("kqmx-ptrw-hncv"))
                .map(ActivationCode::value)
                .contains(CODE);
        assertThat(ActivationCode.retry("KQMX PTRW HNCV"))
                .map(ActivationCode::value)
                .contains(CODE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "KQMXPTRWHNC",
                "KQMXPTRWHNCVA",
                "KQMXPTRWHNC0",
                "KQMXPTRWHNCI",
                "KQMXPTRWHNC1",
                "KQMXPTRWHNCO",
                "KQMXPTRWHN_V",
                "K7QM2XPA"
            })
    void refusesWhatIsOutsideTheFormat(String input) {
        assertThat(ActivationCode.retry(input)).isEmpty();
    }

    @Test
    void ofRequiresTheFormat() {
        assertThat(ActivationCode.of(CODE).value()).isEqualTo(CODE);
        assertThatThrownBy(() -> ActivationCode.of("ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("ABC"));
    }

    @Test
    void looksLikeACode() {
        assertThat(ActivationCode.looksLikeCode("KQMX-PTRW-HNCV")).isTrue();
        assertThat(ActivationCode.looksLikeCode("abc123def456")).isTrue();
        assertThat(ActivationCode.looksLikeCode("olá, tudo bem?")).isFalse();
        assertThat(ActivationCode.looksLikeCode("/start")).isFalse();
        assertThat(ActivationCode.looksLikeCode("K7QM2XPA")).isFalse();
        assertThat(ActivationCode.looksLikeCode(null)).isFalse();
    }

    @Test
    void usesTheSameAlphabetAsTheAccessCodeWithTwelveCharacters() {
        assertThat(ActivationCode.ALPHABET).isEqualTo(AccessCode.ALPHABET);
        assertThat(ActivationCode.SIZE).isEqualTo(12);
    }

    @Test
    void doesNotRevealTheValueAndComparesByValue() {
        ActivationCode code = ActivationCode.of(CODE);

        assertThat(code.toString()).doesNotContain("KQMX");
        assertThat(code)
                .isEqualTo(ActivationCode.of("kqmxptrwhncv"))
                .hasSameHashCodeAs(ActivationCode.of(CODE));
        assertThat(code).isNotEqualTo(ActivationCode.of("KQMXPTRWHNCW")).isNotEqualTo(CODE);
    }
}
