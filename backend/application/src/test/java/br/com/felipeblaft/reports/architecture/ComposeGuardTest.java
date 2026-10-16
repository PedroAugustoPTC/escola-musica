package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardião do compose e do {@code Dockerfile} do n8n (feature 004, R-11): lê {@code
 * infra/compose*.yaml} e {@code infra/n8n/Dockerfile}, offline, no {@code tests-backend}.
 */
class ComposeGuardTest {

    @Test
    @DisplayName("o compose e o Dockerfile do n8n respeitam as regras de contêiner")
    void composeRespectsContainerRules() throws IOException {
        Path infra = GlossaryFile.findRoot().resolve("infra");
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> stream = Files.list(infra)) {
            for (Path file :
                    stream.filter(p -> p.getFileName().toString().matches("compose.*\\.yaml"))
                            .toList()) {
                files.put(file.getFileName().toString(), Files.readString(file));
            }
        }
        Path dockerfile = infra.resolve("n8n/Dockerfile");
        assertThat(Files.exists(dockerfile)).as("infra/n8n/Dockerfile precisa existir").isTrue();

        List<String> violations = ComposeGuard.check(files, Files.readString(dockerfile));

        assertThat(violations).as("violações das regras de contêiner").isEmpty();
    }
}
