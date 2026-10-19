package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guardião de configuração órfã (feature 004, FR-030, SC-010): depois da remoção do caminho antigo
 * do Telegram, nenhuma chave de {@code application.yaml}, {@code .env.example} ou {@code
 * compose*.yaml} pode ficar sem quem a leia, a rota {@code /telegram/webhook} e as variáveis de
 * escolha entre caminhos não podem voltar, e o que o download de áudio e o registro dos comandos
 * usam precisa continuar.
 */
class OrphanConfigurationGuardTest {

    private static final Path ROOT = GlossaryFile.findRoot();
    private static final Path BACKEND = ROOT.resolve("backend/application");
    private static final Path MAIN_JAVA = BACKEND.resolve("src/main/java");
    private static final Path APPLICATION_YAML =
            BACKEND.resolve("src/main/resources/application.yaml");
    private static final Path INFRA = ROOT.resolve("infra");

    /** Removidos com o caminho antigo: não podem aparecer em código, configuração nem scripts. */
    private static final List<String> REMOVED =
            List.of(
                    "/telegram/webhook",
                    "TELEGRAM_CHANNEL_DELIVERY",
                    "TELEGRAM_WEBHOOK_URL",
                    "TELEGRAM_TRANSPORT");

    @Test
    @DisplayName(
            "toda chave reports.* do application.yaml tem um componente em ApplicationProperties")
    void everyYamlKeyIsReadByAClass() throws IOException {
        Map<String, Object> reports = yamlSection("reports");
        List<String> orphans = new ArrayList<>();
        collectOrphans("reports", reports, ApplicationProperties.class, orphans);

        assertThat(orphans).as("chaves de application.yaml que nenhuma classe lê").isEmpty();
    }

    @Test
    @DisplayName("toda variável do backend no compose e no .env.example é lida por alguém")
    void everyEnvironmentVariableIsConsumed() throws IOException {
        String consumers = consumersText();
        List<String> orphans = new ArrayList<>();
        for (String key : environmentKeysOfBackendInCompose()) {
            if (!consumed(key, consumers)) {
                orphans.add("compose.yaml (backend): " + key);
            }
        }
        for (String key : environmentKeysOf(INFRA.resolve(".env.example"))) {
            if (!consumed(key, consumers)) {
                orphans.add(".env.example: " + key);
            }
        }

        assertThat(orphans).as("variáveis que ninguém lê").isEmpty();
    }

    @Test
    @DisplayName("a rota e as variáveis do caminho antigo não existem mais")
    void removedPathLeavesNoTrace() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : filesToScan()) {
            String text = Files.readString(file);
            for (String removed : REMOVED) {
                if (text.contains(removed)) {
                    found.add(ROOT.relativize(file) + " contém " + removed);
                }
            }
        }

        assertThat(found).as("restos do caminho antigo").isEmpty();
    }

    @Test
    @DisplayName("o que o download de áudio e os comandos do bot usam continua")
    void audioDownloadAndCommandsStay() throws IOException {
        String yaml = Files.readString(APPLICATION_YAML);
        for (String variable :
                List.of(
                        "TELEGRAM_API_URL",
                        "TELEGRAM_FILES_DIR",
                        "PENDING_AUDIO_MINUTES",
                        "TELEGRAM_WEBHOOK_SECRET")) {
            assertThat(yaml).as("application.yaml precisa manter %s", variable).contains(variable);
        }
        String code = javaSources();
        assertThat(code).as("getFile (download de áudio)").contains("\"getFile\"");
        assertThat(code).as("setMyCommands (comandos do bot)").contains("\"setMyCommands\"");
    }

    // ---------------------------------------------------------------- chaves do YAML

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yamlSection(String name) throws IOException {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Object document : new Yaml().loadAll(Files.readString(APPLICATION_YAML))) {
            if (document instanceof Map<?, ?> root && root.get(name) instanceof Map<?, ?> section) {
                merged.putAll((Map<String, Object>) section);
            }
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static void collectOrphans(
            String path, Map<String, Object> keys, Class<?> type, List<String> orphans) {
        Map<String, RecordComponent> components = new LinkedHashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            components.put(component.getName().toLowerCase(), component);
        }
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            String key = entry.getKey();
            RecordComponent component = components.get(key.replace("-", "").toLowerCase());
            if (component == null) {
                orphans.add(path + "." + key);
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                collectOrphans(
                        path + "." + key,
                        (Map<String, Object>) nested,
                        component.getType(),
                        orphans);
            }
        }
    }

    // ---------------------------------------------------------------- variáveis de ambiente

    private static List<String> environmentKeysOfBackendInCompose() throws IOException {
        List<String> keys = new ArrayList<>();
        boolean inBackend = false;
        boolean inEnvironment = false;
        for (String line : Files.readAllLines(INFRA.resolve("compose.yaml"))) {
            if (line.matches("^  [A-Za-z0-9_-]+:\\s*$")) {
                inBackend = line.trim().equals("backend:");
                inEnvironment = false;
            } else if (inBackend && line.matches("^    [A-Za-z_-]+:.*$")) {
                inEnvironment = line.trim().equals("environment:");
            } else if (inBackend && inEnvironment) {
                Matcher key = Pattern.compile("^      ([A-Z][A-Z0-9_]+):").matcher(line);
                if (key.find()) {
                    keys.add(key.group(1));
                }
            }
        }
        return keys;
    }

    private static List<String> environmentKeysOf(Path envFile) throws IOException {
        List<String> keys = new ArrayList<>();
        Pattern assignment = Pattern.compile("^#?([A-Z][A-Z0-9_]+)=");
        for (String line : Files.readAllLines(envFile)) {
            Matcher match = assignment.matcher(line);
            if (match.find()) {
                keys.add(match.group(1));
            }
        }
        return keys;
    }

    /** Quem pode ler uma variável: o YAML da aplicação, o entrypoint, o compose e os scripts. */
    private static String consumersText() throws IOException {
        StringBuilder text = new StringBuilder(Files.readString(APPLICATION_YAML));
        append(text, BACKEND.resolve("docker-entrypoint.sh"));
        try (Stream<Path> files = Files.walk(INFRA)) {
            for (Path file :
                    files.filter(Files::isRegularFile)
                            .filter(f -> !f.getFileName().toString().equals(".env.example"))
                            .filter(OrphanConfigurationGuardTest::isTextConfig)
                            .toList()) {
                append(text, file);
            }
        }
        return text.toString();
    }

    private static boolean consumed(String key, String consumers) {
        return Pattern.compile("(?<![A-Z0-9_])" + Pattern.quote(key) + "(?![A-Z0-9_])")
                .matcher(consumers)
                .find();
    }

    private static void append(StringBuilder text, Path file) throws IOException {
        if (Files.exists(file)) {
            text.append('\n').append(Files.readString(file));
        }
    }

    private static boolean isTextConfig(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.endsWith(".sh")
                || name.endsWith(".mjs")
                || name.endsWith(".json")
                || name.endsWith(".template")
                || name.equals("Dockerfile");
    }

    // ---------------------------------------------------------------- varredura

    private static List<Path> filesToScan() throws IOException {
        TreeSet<Path> files = new TreeSet<>();
        files.add(APPLICATION_YAML);
        files.add(INFRA.resolve(".env.example"));
        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            stream.filter(f -> f.toString().endsWith(".java")).forEach(files::add);
        }
        try (Stream<Path> stream = Files.walk(INFRA)) {
            stream.filter(Files::isRegularFile)
                    .filter(
                            f ->
                                    isTextConfig(f)
                                            && !INFRA.relativize(f).startsWith("n8n/node_modules"))
                    .forEach(files::add);
        }
        return new ArrayList<>(files);
    }

    private static String javaSources() throws IOException {
        StringBuilder text = new StringBuilder();
        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            for (Path file : stream.filter(f -> f.toString().endsWith(".java")).toList()) {
                text.append(Files.readString(file)).append('\n');
            }
        }
        return text.toString();
    }
}
