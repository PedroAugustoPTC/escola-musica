package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-071: mudança no que vai a um provedor (adaptadores de IA ou prompts) exige revisar {@code
 * privacy/processing-inventory.md}. A regra mora em {@link InventoryReviewGuard}; o CI a aplica em
 * modo aviso ({@code infra/scripts/check-inventory-review.sh}).
 */
class InventoryReviewGuardTest {

    private static final String INVENTORY = "privacy/processing-inventory.md";

    @Test
    @DisplayName("diff em adaptador de IA sem o inventário é sinalizado")
    void flagsAdapterChangeWithoutInventory() {
        List<String> warnings =
                InventoryReviewGuard.check(
                        List.of(
                                "backend/application/src/main/java/br/com/felipeblaft/reports/outbound/ai/LlmOpenAiCompatibleAdapter.java"));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains(INVENTORY);
    }

    @Test
    @DisplayName("diff em prompt sem o inventário é sinalizado")
    void flagsPromptChangeWithoutInventory() {
        List<String> warnings =
                InventoryReviewGuard.check(
                        List.of(
                                "backend/application/src/main/resources/prompts/lesson-structuring.v1.txt"));

        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("diff que também toca o inventário não é sinalizado")
    void acceptsChangeWithInventory() {
        List<String> warnings =
                InventoryReviewGuard.check(
                        List.of(
                                "backend/application/src/main/resources/prompts/lesson-structuring.v1.txt",
                                INVENTORY));

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("diff sem relação com provedor não é sinalizado")
    void ignoresUnrelatedChange() {
        assertThat(InventoryReviewGuard.check(List.of("frontend/src/app/App.tsx"))).isEmpty();
    }

    @Test
    @DisplayName("o modelo de PR traz o item do inventário e o script de CI existe")
    void pullRequestTemplateAndCiScriptExist() throws IOException {
        Path root = GlossaryFile.findRoot();

        assertThat(Files.readString(root.resolve(".github/pull_request_template.md")))
                .contains("privacy/processing-inventory.md");
        assertThat(root.resolve("infra/scripts/check-inventory-review.sh")).exists();
    }
}
