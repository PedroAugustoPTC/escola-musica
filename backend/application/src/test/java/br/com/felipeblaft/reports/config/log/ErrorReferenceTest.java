package br.com.felipeblaft.reports.config.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ErrorReferenceTest {

    @Test
    void referenceIsEightUppercaseAlphanumericCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(ErrorReference.newReference()).matches("[A-Z0-9]{8}");
        }
    }

    @Test
    void referencesDoNotRepeatInPractice() {
        Set<String> references = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            references.add(ErrorReference.newReference());
        }
        assertThat(references).hasSize(500);
    }
}
