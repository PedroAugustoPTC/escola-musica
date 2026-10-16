package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.ConversationState;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Meta-teste do rastreio de paridade (feature 004, data-model §6.1): cada um dos 10 estados da
 * conversa precisa ter ao menos um teste anotado com {@link ConversationState} entre as classes que
 * a execução {@code n8n-transport} roda (conversa e paridade). Lista os estados sem teste.
 */
class ConversationStateCoverageTest {

    private static final int STATES = 10;

    @Test
    @DisplayName("todo estado da conversa tem ao menos um teste anotado")
    void everyConversationStateHasATest() {
        JavaClasses classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                        .importPackages(
                                "br.com.felipeblaft.reports.inbound.telegram",
                                "br.com.felipeblaft.reports.parity");

        Set<Integer> covered = new TreeSet<>();
        for (JavaClass type : classes) {
            if (!type.getSimpleName().endsWith("IT")) {
                continue;
            }
            type.tryGetAnnotationOfType(ConversationState.class)
                    .ifPresent(annotation -> add(covered, annotation.value()));
            for (JavaMethod method : type.getMethods()) {
                method.tryGetAnnotationOfType(ConversationState.class)
                        .ifPresent(annotation -> add(covered, annotation.value()));
            }
        }

        Set<Integer> missing = new TreeSet<>();
        for (int state = 1; state <= STATES; state++) {
            if (!covered.contains(state)) {
                missing.add(state);
            }
        }
        assertThat(missing)
                .as("estados da conversa (1 a 10) sem nenhum teste anotado em @ConversationState")
                .isEmpty();
    }

    private static void add(Set<Integer> covered, int[] states) {
        for (int state : states) {
            assertThat(state).as("estado fora de 1 a 10").isBetween(1, STATES);
            covered.add(state);
        }
    }
}
