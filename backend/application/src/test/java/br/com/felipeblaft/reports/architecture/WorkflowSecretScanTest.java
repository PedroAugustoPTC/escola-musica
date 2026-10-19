package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Varredura de segredos e de IDs de credencial nos arquivos versionados do n8n (feature 004,
 * FR-009, SC-005, US1 cenário 4): os workflows e os demais arquivos de {@code infra/n8n/}. Não há
 * lista de exceção; o único valor ignorado é o <em>digest</em> da imagem, que é público.
 */
class WorkflowSecretScanTest {

    @Test
    @DisplayName("nenhum arquivo versionado de infra/n8n tem segredo nem ID de credencial")
    void versionedFilesHaveNoSecrets() throws IOException {
        Path root = GlossaryFile.findRoot();
        Path n8n = root.resolve("infra/n8n");
        assertThat(Files.isDirectory(n8n)).as("a pasta infra/n8n precisa existir").isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(n8n)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                violations.addAll(SecretScanner.scan(relative, Files.readString(file)));
            }
        }

        assertThat(violations)
                .as("segredos encontrados (arquivo, linha e regra; nunca o valor)")
                .isEmpty();
    }
}
