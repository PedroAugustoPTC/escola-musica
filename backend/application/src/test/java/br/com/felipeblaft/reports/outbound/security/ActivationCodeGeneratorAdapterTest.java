package br.com.felipeblaft.reports.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.ActivationCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Código de ativação do administrador (R-07): 12 caracteres do alfabeto sem ambíguos, gerados com
 * fonte aleatória criptográfica e sem repetição prática; o valor não aparece no {@code toString}.
 */
class ActivationCodeGeneratorAdapterTest {

    private final ActivationCodeGeneratorAdapter generator = new ActivationCodeGeneratorAdapter();

    @Test
    void generatesTwelveCharactersFromTheUnambiguousAlphabet() {
        for (int i = 0; i < 200; i++) {
            String value = generator.generate().value();

            assertThat(value).hasSize(12);
            assertThat(value.chars()).allMatch(c -> AccessCode.ALPHABET.indexOf(c) >= 0);
        }
    }

    @Test
    void generatedCodeIsAcceptedByTheDomainParser() {
        ActivationCode code = generator.generate();

        assertThat(ActivationCode.retry(code.value())).isPresent();
        assertThat(ActivationCode.looksLikeCode(code.value())).isTrue();
    }

    @Test
    void codesDoNotRepeatInPractice() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(generator.generate().value());
        }

        assertThat(seen).hasSize(2000);
    }

    @Test
    void usesTheWholeAlphabet() {
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            generator.generate().value().chars().forEach(c -> seen.add((char) c));
        }

        assertThat(seen).hasSize(AccessCode.ALPHABET.length());
    }

    @Test
    void toStringNeverRevealsTheValue() {
        ActivationCode code = generator.generate();

        assertThat(code.toString()).doesNotContain(code.value());
    }
}
