package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.felipeblaft.reports.domain.access.ActivationCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link ActivationCode} é esqueleto da Fundação até o fluxo W-A (T035); enquanto isso estes testes
 * são pulados, e passam a rodar sozinhos quando a classe for implementada.
 */
class FixedActivationCodeGeneratorTest {

    private static final String FIRST = "KQMXPTRWHNCV";
    private static final String SECOND = "ABCDEFGHJKLM";

    @BeforeAll
    static void requireActivationCode() {
        boolean implemented;
        try {
            ActivationCode.retry(FIRST);
            implemented = true;
        } catch (UnsupportedOperationException skeleton) {
            implemented = false;
        }
        assumeTrue(implemented, "ActivationCode ainda é esqueleto (fluxo W-A, T035)");
    }

    @Test
    void returnsTheScheduledCodesInOrder() {
        FixedActivationCodeGenerator generator = new FixedActivationCodeGenerator(FIRST, SECOND);

        assertThat(generator.generate().value()).isEqualTo(FIRST);
        assertThat(generator.generate().value()).isEqualTo(SECOND);
    }

    @Test
    void schedulesMoreCodesAfterCreation() {
        FixedActivationCodeGenerator generator = new FixedActivationCodeGenerator();
        generator.schedule(SECOND);

        assertThat(generator.generate().value()).isEqualTo(SECOND);
    }

    @Test
    void afterTheScheduledCodesItGeneratesPredictableDistinctValidOnes() {
        FixedActivationCodeGenerator generator = new FixedActivationCodeGenerator();
        Set<String> values = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            ActivationCode code = generator.generate();
            assertThat(code.value()).hasSize(12);
            assertThat(ActivationCode.retry(code.value())).isPresent();
            values.add(code.value());
        }

        assertThat(values).hasSize(50);
    }

    @Test
    void twoGeneratorsProduceTheSameSequence() {
        FixedActivationCodeGenerator one = new FixedActivationCodeGenerator();
        FixedActivationCodeGenerator other = new FixedActivationCodeGenerator();

        assertThat(one.generate().value()).isEqualTo(other.generate().value());
        assertThat(one.generate().value()).isEqualTo(other.generate().value());
    }
}
