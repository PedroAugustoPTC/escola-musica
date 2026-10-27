package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfShapeTest {

    @Test
    void recognizesElevenDigitsWithOrWithoutSeparators() {
        assertThat(CpfShape.matches("52998224725")).isTrue();
        assertThat(CpfShape.matches("529.982.247-25")).isTrue();
        assertThat(CpfShape.matches("529 982 247 25")).isTrue();
        assertThat(CpfShape.matches("529.982.247/25")).isTrue();
        assertThat(CpfShape.matches(" 529-982-247-25 ")).isTrue();
        assertThat(CpfShape.matches("529982247 - 25")).isTrue();
    }

    @Test
    void testsExactlyElevenDigits() {
        assertThat(CpfShape.matches("5299822472")).isFalse();
        assertThat(CpfShape.matches("529982247255")).isFalse();
        assertThat(CpfShape.matches("529.982.247-2")).isFalse();
        assertThat(CpfShape.matches("5299822472a")).isFalse();
        assertThat(CpfShape.matches("meu cpf 52998224725")).isFalse();
        assertThat(CpfShape.matches("(11) 98888-7777")).isFalse();
        assertThat(CpfShape.matches("")).isFalse();
        assertThat(CpfShape.matches("   ")).isFalse();
        assertThat(CpfShape.matches(null)).isFalse();
    }

    @Test
    void validityUsesTheCheckDigitsOfTheCore() {
        assertThat(CpfShape.isValid("529.982.247-25")).isTrue();
        assertThat(CpfShape.isValid("52998224725")).isTrue();
        assertThat(CpfShape.isValid("529 982 247 25")).isTrue();
        assertThat(CpfShape.isValid("529.982.247-26")).isFalse();
        assertThat(CpfShape.isValid("111.111.111-11")).isFalse();
        assertThat(CpfShape.isValid("Maria")).isFalse();
        assertThat(CpfShape.isValid(null)).isFalse();
    }
}
