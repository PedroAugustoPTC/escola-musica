package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Cpf;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanaryCpfTest {

    @Test
    void isAValidCpfForTheDomain() {
        Cpf cpf = CanaryCpf.cpf();

        assertThat(cpf.digits()).isEqualTo(CanaryCpf.DIGITS);
    }

    @Test
    void maskedFormMatchesTheDigits() {
        assertThat(CanaryCpf.MASKED.replaceAll("\\D", "")).isEqualTo(CanaryCpf.DIGITS);
        assertThat(Cpf.of(CanaryCpf.MASKED)).isEqualTo(CanaryCpf.cpf());
    }

    @Test
    void sweepValuesCarryTheDigitsUnderASingleLabel() {
        assertThat(CanaryCpf.sweepValues())
                .containsExactly(Map.entry("CPF canário", CanaryCpf.DIGITS));
    }

    @Test
    void toStringOfTheDomainCpfDoesNotRevealIt() {
        assertThat(CanaryCpf.cpf().toString()).doesNotContain(CanaryCpf.DIGITS);
    }
}
