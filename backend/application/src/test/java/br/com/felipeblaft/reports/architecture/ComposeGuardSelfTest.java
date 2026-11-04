package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Autoteste do guardião do compose: parte de um compose válido e aplica um defeito por regra; o
 * guardião precisa reprovar exatamente aquela regra.
 */
class ComposeGuardSelfTest {

    private static final String BASE_YAML =
            """
            x-logging: &default-logging
              driver: json-file
              options:
                max-size: "20m"
                max-file: "5"
            services:
              db:
                image: postgres:18
                logging: *default-logging
              n8n:
                build: ./n8n
                image: reports-n8n
                logging: *default-logging
                networks: [internal]
                environment:
                  N8N_DISABLE_UI: "true"
                  BACKEND_SERVICE_TOKEN: ${TELEGRAM_CHANNEL_CURRENT_TOKEN}
            networks:
              internal:
            """;

    private static final String DEV_YAML =
            """
            services:
              n8n:
                environment:
                  N8N_DISABLE_UI: "false"
                ports:
                  - "127.0.0.1:5678:5678"
            """;

    private static final String PROD_YAML =
            """
            services:
              n8n:
                restart: unless-stopped
                environment:
                  N8N_DISABLE_UI: "true"
            """;

    private static final String DOCKERFILE =
            """
            FROM docker.n8n.io/n8nio/n8n:2.43.6
            USER node
            """;

    private static Map<String, String> valid() {
        return Map.of(
                ComposeGuard.BASE,
                BASE_YAML,
                ComposeGuard.DEV,
                DEV_YAML,
                ComposeGuard.PROD,
                PROD_YAML);
    }

    private static Map<String, String> mutate(String file, UnaryOperator<String> change) {
        Map<String, String> files = new java.util.HashMap<>(valid());
        files.put(file, change.apply(files.get(file)));
        return files;
    }

    static Stream<Arguments> defects() {
        return Stream.of(
                Arguments.of(
                        "imagem sem tag",
                        mutate(ComposeGuard.BASE, y -> y.replace("postgres:18", "postgres")),
                        DOCKERFILE,
                        "IMAGE_UNTAGGED"),
                Arguments.of(
                        "imagem latest",
                        mutate(ComposeGuard.BASE, y -> y.replace("postgres:18", "postgres:latest")),
                        DOCKERFILE,
                        "IMAGE_FLOATING"),
                Arguments.of(
                        "imagem stable",
                        mutate(ComposeGuard.BASE, y -> y.replace("postgres:18", "postgres:stable")),
                        DOCKERFILE,
                        "IMAGE_FLOATING"),
                Arguments.of(
                        "porta na base",
                        mutate(
                                ComposeGuard.BASE,
                                y ->
                                        y.replace(
                                                "networks: [internal]",
                                                "networks: [internal]\n    ports: [\"5678:5678\"]")),
                        DOCKERFILE,
                        "N8N_PORTS"),
                Arguments.of(
                        "porta fora do loopback no dev",
                        mutate(
                                ComposeGuard.DEV,
                                y -> y.replace("127.0.0.1:5678:5678", "5678:5678")),
                        DOCKERFILE,
                        "N8N_PORTS"),
                Arguments.of(
                        "porta em produção",
                        mutate(
                                ComposeGuard.PROD,
                                y ->
                                        y.replace(
                                                "restart: unless-stopped",
                                                "restart: unless-stopped\n    ports: [\"127.0.0.1:5678:5678\"]")),
                        DOCKERFILE,
                        "N8N_PORTS"),
                Arguments.of(
                        "fora da rede interna",
                        mutate(
                                ComposeGuard.BASE,
                                y ->
                                        y.replace(
                                                "networks: [internal]",
                                                "networks: [internal, other]")),
                        DOCKERFILE,
                        "N8N_NETWORK"),
                Arguments.of(
                        "sem reinício em produção",
                        mutate(
                                ComposeGuard.PROD,
                                y -> y.replace("restart: unless-stopped", "restart: \"no\"")),
                        DOCKERFILE,
                        "N8N_NO_RESTART"),
                Arguments.of(
                        "editor ligado na base",
                        mutate(
                                ComposeGuard.BASE,
                                y ->
                                        y.replace(
                                                "N8N_DISABLE_UI: \"true\"",
                                                "N8N_DISABLE_UI: \"false\"")),
                        DOCKERFILE,
                        "N8N_UI_ENABLED"),
                Arguments.of(
                        "editor ligado em produção",
                        mutate(
                                ComposeGuard.PROD,
                                y ->
                                        y.replace(
                                                "N8N_DISABLE_UI: \"true\"",
                                                "N8N_DISABLE_UI: \"false\"")),
                        DOCKERFILE,
                        "N8N_UI_ENABLED"),
                Arguments.of(
                        "segredo literal",
                        mutate(
                                ComposeGuard.BASE,
                                y ->
                                        y.replace(
                                                "${TELEGRAM_CHANNEL_CURRENT_TOKEN}",
                                                "literal-value")),
                        DOCKERFILE,
                        "LITERAL_SECRET"),
                Arguments.of(
                        "FROM sem versão exata",
                        valid(),
                        "FROM docker.n8n.io/n8nio/n8n:2\n",
                        "DOCKERFILE_FROM"),
                Arguments.of(
                        "FROM latest",
                        valid(),
                        "FROM docker.n8n.io/n8nio/n8n:latest\n",
                        "DOCKERFILE_FROM"),
                Arguments.of(
                        "serviço sem rotação de logs",
                        mutate(
                                ComposeGuard.BASE,
                                y ->
                                        y.replaceFirst(
                                                "(image: postgres:18)\\n\\s+logging: \\*default-logging",
                                                "$1")),
                        DOCKERFILE,
                        "LOG_ROTATION"),
                Arguments.of(
                        "rotação de logs sem limite de arquivos",
                        mutate(
                                ComposeGuard.BASE,
                                y -> y.replace("max-file: \"5\"", "max-file: \"0\"")),
                        DOCKERFILE,
                        "LOG_ROTATION"),
                Arguments.of(
                        "n8n ausente",
                        Map.of(ComposeGuard.BASE, "services:\n  db:\n    image: postgres:18\n"),
                        DOCKERFILE,
                        "N8N_MISSING"));
    }

    @ParameterizedTest(name = "{0} reprova com {3}")
    @MethodSource("defects")
    @DisplayName("cada regra reprova o compose com defeito")
    void eachRuleRejectsItsDefect(
            String description, Map<String, String> files, String dockerfile, String code) {
        List<String> violations = ComposeGuard.check(files, dockerfile);

        assertThat(violations).anyMatch(v -> v.startsWith("[" + code + "]"));
    }

    @Test
    @DisplayName("o compose válido não gera violação")
    void validComposePasses() {
        assertThat(ComposeGuard.check(valid(), DOCKERFILE)).isEmpty();
    }
}
