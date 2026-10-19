package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardião dos workflows do n8n (feature 004, R-05, FR-009 a FR-011): o n8n só transporta. Lê
 * {@code infra/n8n/workflows/*.json} e roda offline, no {@code tests-backend}.
 */
class WorkflowGuardTest {

    /** Arquivos que já precisam existir nesta fase; as fases seguintes ampliam a lista. */
    private static final Set<String> REQUIRED =
            Set.of("transport-health", "telegram-inbound", "execute-actions", "channel-delivery");

    @Test
    @DisplayName("os workflows do repositório respeitam as regras de transporte")
    void repositoryWorkflowsRespectTransportRules() {
        Path directory = GlossaryFile.findRoot().resolve("infra/n8n/workflows");
        assertThat(Files.isDirectory(directory))
                .as("a pasta infra/n8n/workflows precisa existir")
                .isTrue();

        List<String> violations = WorkflowGuard.check(directory, REQUIRED);

        assertThat(violations)
                .as("violações das regras dos workflows (arquivo, nó e caminho; nunca o valor)")
                .isEmpty();
    }
}
