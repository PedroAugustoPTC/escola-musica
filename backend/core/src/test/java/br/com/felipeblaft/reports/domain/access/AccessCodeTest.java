package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccessCodeTest {

    @Test
    void normalizesSpacesAndCase() {
        assertThat(AccessCode.retry("  k7qm2xpa ")).map(AccessCode::value).contains("K7QM2XPA");
    }

    @Test
    void acceptsHyphenGroupedForm() {
        assertThat(AccessCode.retry("k7qm-2xpa")).map(AccessCode::value).contains("K7QM2XPA");
        assertThat(AccessCode.retry("K7QM 2XPA")).map(AccessCode::value).contains("K7QM2XPA");
    }

    @Test
    void looksLikeCode() {
        assertThat(AccessCode.looksLikeCode("K7QM-2XPA")).isTrue();
        assertThat(AccessCode.looksLikeCode("abc123")).isTrue();
        assertThat(AccessCode.looksLikeCode("olá, tudo bem?")).isFalse();
        assertThat(AccessCode.looksLikeCode("/start")).isFalse();
        assertThat(AccessCode.looksLikeCode(null)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {"", "K7QM2XP", "K7QM2XPAB", "K7QM2XP0", "K7QM2XPI", "K7QM2XP1", "K7QM_XPA"})
    void refusalOutsideFormat(String input) {
        assertThat(AccessCode.retry(input)).isEmpty();
    }

    @Test
    void ofRequiresValidFormat() {
        assertThat(AccessCode.of("ABCDEFGH").value()).isEqualTo("ABCDEFGH");
        assertThatThrownBy(() -> AccessCode.of("ABC")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alphabetWithoutAmbiguousCharacters() {
        assertThat(AccessCode.ALPHABET).doesNotContain("0", "1", "I", "O").hasSize(32);
    }

    @Test
    void doesNotRevealValueAndComparesByValue() {
        AccessCode code = AccessCode.of("K7QM2XPA");
        assertThat(code.toString()).doesNotContain("K7QM");
        assertThat(code)
                .isEqualTo(AccessCode.of("k7qm2xpa"))
                .hasSameHashCodeAs(AccessCode.of("K7QM2XPA"));
        assertThat(code).isNotEqualTo(AccessCode.of("K7QM2XPB")).isNotEqualTo("K7QM2XPA");
    }
}
