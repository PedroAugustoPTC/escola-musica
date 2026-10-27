package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InstrumentTest {

    private static void refuses(String text, ErrorCode expected) {
        assertThatThrownBy(() -> Instrument.of(text))
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    @Test
    void trimsAndNormalizesToNfc() {
        assertThat(Instrument.of("  Violão ").value()).isEqualTo("Violão");
        assertThat(Instrument.of("Violão").value()).isEqualTo("Violão");
    }

    @Test
    void sixtyCharactersAreAcceptedAndSixtyOneAreNot() {
        assertThat(Instrument.of("a".repeat(60)).value()).hasSize(60);
        refuses("a".repeat(61), ErrorCode.INVALID_INSTRUMENT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void absentOrBlankIsNotAnInstrument(String text) {
        refuses(text, ErrorCode.INVALID_INSTRUMENT);
    }

    @Test
    void controlCharactersAreRefusedAsInvalidText() {
        refuses("Pia\u0000no", ErrorCode.INVALID_TEXT);
        refuses("Pia\tno", ErrorCode.INVALID_TEXT);
    }

    @Test
    void equalityByValueAndNoDataInText() {
        Instrument piano = Instrument.of("Piano");
        assertThat(piano).isEqualTo(Instrument.of(" Piano "));
        assertThat(piano).hasSameHashCodeAs(Instrument.of("Piano"));
        assertThat(piano).isNotEqualTo(Instrument.of("Violão")).isNotEqualTo("Piano");
        assertThat(piano.toString()).doesNotContain("Piano");
    }
}
