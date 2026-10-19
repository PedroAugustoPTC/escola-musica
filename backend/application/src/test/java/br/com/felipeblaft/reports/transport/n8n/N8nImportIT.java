package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.execInN8n;
import static br.com.felipeblaft.reports.support.N8nTestSupport.get;
import static br.com.felipeblaft.reports.support.N8nTestSupport.healthStatus;
import static br.com.felipeblaft.reports.support.N8nTestSupport.restartN8n;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.architecture.GlossaryFile;
import br.com.felipeblaft.reports.support.N8nTestSupport.CommandResult;
import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Importação dos workflows do repositório pelo n8n real (feature 004, US1, FR-008): a partir de
 * volume vazio o n8n fica saudável com o conjunto exato do repositório ativo; a edição manual na
 * instância é detectada pelo {@code check} e descartada no reinício.
 */
@Tag("n8n")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class N8nImportIT {

    private static final String PUBLISHED_IDS_SCRIPT =
            "const { DatabaseSync } = require('node:sqlite');"
                    + "const db = new DatabaseSync(process.env.DB_SQLITE_DATABASE);"
                    + "console.log(db.prepare('select id from workflow_entity where activeVersionId is not null order by id')"
                    + ".all().map((row) => row.id).join(','));";

    private static final String EDIT_SCRIPT =
            "const fs = require('fs');"
                    + "const w = JSON.parse(fs.readFileSync('/opt/workflows/transport-health.json', 'utf8'));"
                    + "w.nodes[0].parameters.path = 'edited-by-hand';"
                    + "fs.mkdirSync('/tmp/edited', { recursive: true });"
                    + "fs.writeFileSync('/tmp/edited/transport-health.json', JSON.stringify(w));";

    @Test
    @Order(1)
    @DisplayName("a partir de volume vazio o n8n fica saudável e responde ao live")
    void becomesHealthyAndAnswersLive() {
        awaitHealthy(Duration.ofSeconds(180));

        Reply live = get(N8N_URL + "/webhook/transport-health/live");

        assertThat(healthStatus()).isEqualTo("healthy");
        assertThat(live.status()).isEqualTo(200);
        assertThat(live.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @Order(2)
    @DisplayName("os workflows publicados são exatamente os do repositório")
    void publishedWorkflowsAreExactlyTheRepositoryOnes() throws IOException {
        awaitHealthy(Duration.ofSeconds(180));

        CommandResult published = execInN8n("node", "--no-warnings", "-e", PUBLISHED_IDS_SCRIPT);

        assertThat(published.exitCode()).as(published.output()).isZero();
        assertThat(Arrays.stream(published.output().trim().split(",")).sorted().toList())
                .isEqualTo(repositoryWorkflowIds());
    }

    @Test
    @Order(3)
    @DisplayName("o check não acusa divergência logo após a subida")
    void checkHasNoDivergenceAfterStartup() {
        CommandResult check = execInN8n("sh", "/opt/ops/check-workflows.sh");

        assertThat(check.exitCode()).as(check.output()).isZero();
    }

    @Test
    @Order(4)
    @DisplayName("edição manual na instância é detectada pelo check e descartada no reinício")
    void manualEditIsDetectedAndDiscardedOnRestart() {
        assertThat(execInN8n("node", "-e", EDIT_SCRIPT).exitCode()).isZero();
        CommandResult imported =
                execInN8n("n8n", "import:workflow", "--separate", "--input=/tmp/edited");
        assertThat(imported.exitCode()).as(imported.output()).isZero();

        CommandResult diverged = execInN8n("sh", "/opt/ops/check-workflows.sh");
        assertThat(diverged.exitCode()).isEqualTo(1);
        assertThat(diverged.output())
                .contains("transport-health")
                .contains("$.nodes[0].parameters.path")
                .doesNotContain("edited-by-hand");

        restartN8n();
        awaitHealthy(Duration.ofSeconds(180));

        CommandResult restored = execInN8n("sh", "/opt/ops/check-workflows.sh");
        assertThat(restored.exitCode()).as(restored.output()).isZero();
        assertThat(get(N8N_URL + "/webhook/transport-health/live").status()).isEqualTo(200);
    }

    private static List<String> repositoryWorkflowIds() throws IOException {
        Path directory = GlossaryFile.findRoot().resolve("infra/n8n/workflows");
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }
}
