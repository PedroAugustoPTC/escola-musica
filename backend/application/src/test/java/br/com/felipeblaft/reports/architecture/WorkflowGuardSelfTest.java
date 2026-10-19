package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Autoteste do guardião de workflows: cada regra tem um JSON com defeito em {@code
 * src/test/resources/workflow-guard/<caso>/}, e o guardião precisa reprovar exatamente aquela
 * regra. Prova que o guardião reprova de verdade (e que o caso válido passa).
 */
class WorkflowGuardSelfTest {

    private static Path fixtures() {
        return Path.of("src/test/resources/workflow-guard").toAbsolutePath();
    }

    static Stream<Arguments> defects() {
        return Stream.of(
                Arguments.of("second-update-receiver", "UPDATE_RECEIVER"),
                Arguments.of("telegram-trigger", "UPDATE_RECEIVER"),
                Arguments.of("database-node", "NODE_NOT_ALLOWED"),
                Arguments.of("sticky-note", "NODE_NOT_ALLOWED"),
                Arguments.of("external-url", "DESTINATION"),
                Arguments.of("credentials", "CREDENTIALS"),
                Arguments.of("env-not-allowed", "ENV_NOT_ALLOWED"),
                Arguments.of("get-file", "GET_FILE"),
                Arguments.of("code-require", "CODE_FORBIDDEN"),
                Arguments.of("code-env", "CODE_FORBIDDEN"),
                Arguments.of("code-comment", "FREE_TEXT"),
                Arguments.of("branch-on-content", "BRANCH_ON_CONTENT"),
                Arguments.of("free-text", "FREE_TEXT"),
                Arguments.of("unknown-file", "UNKNOWN_FILE"),
                Arguments.of("id-mismatch", "ID_MISMATCH"));
    }

    @ParameterizedTest(name = "{0} reprova com {1}")
    @MethodSource("defects")
    @DisplayName("cada regra reprova o JSON com defeito")
    void eachRuleRejectsItsDefect(String fixture, String code) {
        List<String> violations = WorkflowGuard.check(fixtures().resolve(fixture), Set.of());

        assertThat(violations).anyMatch(v -> v.startsWith("[" + code + "]"));
    }

    @Test
    @DisplayName("o workflow válido não gera violação")
    void validWorkflowPasses() {
        assertThat(WorkflowGuard.check(fixtures().resolve("valid"), Set.of("transport-health")))
                .isEmpty();
    }

    @Test
    @DisplayName("arquivo obrigatório ausente reprova")
    void missingRequiredFileFails() {
        List<String> violations =
                WorkflowGuard.check(fixtures().resolve("valid"), Set.of("telegram-inbound"));

        assertThat(violations).anyMatch(v -> v.startsWith("[MISSING_FILE]"));
    }

    @Test
    @DisplayName("a mensagem cita arquivo e caminho, nunca o valor encontrado")
    void messageNeverRevealsTheValue() {
        List<String> violations = WorkflowGuard.check(fixtures().resolve("credentials"), Set.of());

        assertThat(violations)
                .isNotEmpty()
                .allSatisfy(
                        v -> {
                            assertThat(v).contains("execute-actions.json");
                            assertThat(v).doesNotContain("fake-credential-id");
                        });
    }
}
