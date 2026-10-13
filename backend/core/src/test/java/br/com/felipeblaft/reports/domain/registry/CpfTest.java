package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    @Test
    void normalizesPunctuationToElevenDigits() {
        assertThat(Cpf.of("529.982.247-25").digits()).isEqualTo("52998224725");
        assertThat(Cpf.of(" 529 982 247 25 ").digits()).isEqualTo("52998224725");
        assertThat(Cpf.of("123.456.789-09").digits()).isEqualTo("12345678909");
    }

    @Test
    void acceptsZeroCheckDigit() {
        assertThat(Cpf.of("98765432100").digits()).isEqualTo("98765432100");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "529.982.247-24",
                "529.982.247-15",
                "1234567890",
                "123456789012",
                "111.111.111-11",
                "abc.def.ghi-jk"
            })
    void refusesInvalidCpf(String input) {
        assertThatThrownBy(() -> Cpf.of(input))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.CPF_INVALIDO);
    }

    @Test
    void doesNotRevealValueInText() {
        assertThat(Cpf.of("529.982.247-25").toString()).doesNotContain("529").contains("***");
    }

    @Test
    void equalityByDigits() {
        assertThat(Cpf.of("529.982.247-25")).isEqualTo(Cpf.of("52998224725"));
        assertThat(Cpf.of("529.982.247-25")).hasSameHashCodeAs(Cpf.of("52998224725"));
        assertThat(Cpf.of("529.982.247-25")).isNotEqualTo(Cpf.of("123.456.789-09"));
        assertThat(Cpf.of("529.982.247-25")).isNotEqualTo("52998224725");
    }
}
