package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonNameTest {

    private static void refuses(String text, ErrorCode expected) {
        assertThatThrownBy(() -> PersonName.of(text))
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    @Test
    void trimsTheEndsAndKeepsTheInnerText() {
        assertThat(PersonName.of("  Matteo Mafra \n").value()).isEqualTo("Matteo Mafra");
    }

    @Test
    void normalizesToNfc() {
        String decomposed = "José Silva";
        assertThat(PersonName.of(decomposed).value()).isEqualTo("José Silva");
    }

    @Test
    void acceptsTheBoundaryLengths() {
        assertThat(PersonName.of("Al").value()).hasSize(2);
        assertThat(PersonName.of("a".repeat(120)).value()).hasSize(120);
    }

    @Test
    void refusesOutsideTheBoundaryLengths() {
        refuses("A", ErrorCode.INVALID_NAME);
        refuses("a".repeat(121), ErrorCode.INVALID_NAME);
        refuses("  A  ", ErrorCode.INVALID_NAME);
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        String emoji = "😀";
        assertThat(PersonName.of(emoji.repeat(120)).value().codePointCount(0, 240)).isEqualTo(120);
        refuses(emoji.repeat(121), ErrorCode.INVALID_NAME);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void absentOrBlankIsInvalid(String text) {
        refuses(text, ErrorCode.INVALID_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ana\u0000Maria", "Ana\tMaria", "Ana\nMaria", "Ana\u007fMaria"})
    void controlCharactersAreRefusedAsInvalidText(String text) {
        refuses(text, ErrorCode.INVALID_TEXT);
    }

    @Test
    void equalityByValueAndNoPersonalDataInText() {
        PersonName name = PersonName.of("Matteo Mafra");
        assertThat(name).isEqualTo(PersonName.of(" Matteo Mafra "));
        assertThat(name).hasSameHashCodeAs(PersonName.of("Matteo Mafra"));
        assertThat(name).isNotEqualTo(PersonName.of("Outra Pessoa")).isNotEqualTo("Matteo Mafra");
        assertThat(name.toString()).doesNotContain("Matteo").doesNotContain("Mafra");
    }
}
