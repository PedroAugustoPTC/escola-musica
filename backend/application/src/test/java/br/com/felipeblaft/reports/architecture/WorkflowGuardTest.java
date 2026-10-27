package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

    @Test
    @DisplayName("execute-actions executa deleteMessage como transporte puro (feature 005, FR-036)")
    void executeActionsDeletesMessagesAsPureTransport() throws Exception {
        Path file = GlossaryFile.findRoot().resolve("infra/n8n/workflows/execute-actions.json");
        JsonNode root = JsonMapper.builder().build().readTree(Files.readString(file));
        String all = root.toString();

        List<String> kinds = new java.util.ArrayList<>();
        for (JsonNode node : root.path("nodes")) {
            if (node.path("name").asString().equals("Kind")) {
                for (JsonNode rule : node.path("parameters").path("rules").path("values")) {
                    kinds.add(rule.path("outputKey").asString());
                }
            }
        }
        assertThat(kinds).as("tipos despachados pelo nó Kind").contains("DELETE_MESSAGE");
        assertThat(all).contains("deleteMessage").contains("MESSAGE_NOT_DELETABLE");
        // só transporte: o aviso ao professor e a compensação são do backend, nunca do workflow
        assertThat(all)
                .doesNotContain("CPF_MESSAGE_NOT_DELETED")
                .doesNotContain("Apague-a")
                .doesNotContain("compensat");
        assertThat(WorkflowGuard.check(file.getParent(), REQUIRED))
                .as("o guardião segue sem violações com a ação nova")
                .isEmpty();
    }
}
