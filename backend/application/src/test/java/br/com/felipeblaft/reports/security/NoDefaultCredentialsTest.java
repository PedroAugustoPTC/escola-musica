package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.architecture.GlossaryFile;
import br.com.felipeblaft.reports.architecture.RepositoryWalker;
import br.com.felipeblaft.reports.architecture.RepositoryWalker.Entry;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-034 e SC-009: nenhuma credencial de administrador vem no repositório. Nenhum arquivo
 * versionado de produção, infraestrutura ou documentação traz senha, hash Argon2 ou segredo de
 * administrador; as variáveis {@code ADMIN_BOOTSTRAP_*} do {@code infra/.env.example} estão vazias
 * e nenhum arquivo dá valor padrão a elas. A mensagem de falha cita o arquivo, a linha e a regra,
 * nunca o valor.
 *
 * <p>Ficam fora da varredura só o que não é versionado ({@code infra/.env}, as cargas reais em
 * {@code infra/seed}, o ambiente do ensaio) e as árvores de teste, cujos hashes e senhas são
 * amostras fictícias de teste unitário (provadas, em tempo de execução, por {@code
 * AdminSecretsNotLoggedIT} e {@code NoDefaultCredentialsLeakIT}).
 */
class NoDefaultCredentialsTest {

    /** Arquivos que o {@code .gitignore} mantém fora do repositório (podem existir localmente). */
    private static final Set<String> UNVERSIONED =
            Set.of(
                    "infra/.env",
                    "infra/.env.previous",
                    "infra/tests/rehearsal/rehearsal.env",
                    "infra/tests/rehearsal/rehearsal.env.previous");

    private static final Set<String> VERSIONED_SEEDS =
            Set.of("infra/seed/example.json", "infra/seed/demo.json");

    private static final Pattern TEST_TREE =
            Pattern.compile("(backend/[^/]+/src/test/.*|frontend/tests/.*)");

    /** Pastas de trabalho e de ferramentas que não são conteúdo do repositório. */
    private static final Pattern WORK_TREE =
            Pattern.compile("(\\.claude/(worktrees|agent-memory)|\\.code-review-graph)(/.*)?");

    private static final Pattern ARGON2_HASH =
            Pattern.compile("\\$argon2(?:id|i|d)\\$v=\\d+\\$m=\\d+,t=\\d+,p=\\d+\\$");
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{50,}");
    private static final Pattern PBKDF2_HASH = Pattern.compile("\\$pbkdf2-[a-z0-9]+\\$");

    /** {@code ADMIN_BOOTSTRAP_X=valor} ou {@code ADMIN_BOOTSTRAP_X: valor} com valor literal. */
    private static final Pattern BOOTSTRAP_ASSIGNMENT =
            Pattern.compile("(?<!\\{)\\bADMIN_BOOTSTRAP_(NAME|EMAIL|PASSWORD)\\b\\s*[:=]\\s*(\\S.*)$");

    /** {@code ${ADMIN_BOOTSTRAP_X:padrão}} e {@code ${ADMIN_BOOTSTRAP_X:-padrão}}. */
    private static final Pattern BOOTSTRAP_DEFAULT =
            Pattern.compile("\\$\\{ADMIN_BOOTSTRAP_(?:NAME|EMAIL|PASSWORD):-?([^}]*)}");

    /** Variáveis de senha, hash ou segredo de administrador com valor literal. */
    private static final Pattern SECRET_ASSIGNMENT =
            Pattern.compile(
                    "(?<!\\{)\\b(ADMIN_[A-Z_]*(?:PASSWORD|HASH|SECRET)|ADMIN_ACTIVATION_CODE)\\b\\s*[:=]\\s*(\\S.*)$");

    private static final Pattern ADMIN_PASSWORD_PROPERTY =
            Pattern.compile("(?i)\\badministrator\\W+bootstrap\\W+password\\s*[:=]\\s*(\\S.*)$");

    @Test
    @DisplayName("nenhum arquivo versionado traz senha, hash ou segredo de administrador")
    void noVersionedFileCarriesAdministratorCredentials() throws IOException {
        Path root = GlossaryFile.load().root();
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        for (Entry entry : new RepositoryWalker(root).walk()) {
            if (entry.directory() || !scannable(entry.path())) {
                continue;
            }
            String content = readText(root.resolve(entry.path()));
            if (content == null) {
                continue;
            }
            scanned++;
            violations.addAll(scan(entry.path(), content));
        }
        assertThat(scanned).as("a varredura precisa examinar os arquivos do repositório").isGreaterThan(200);
        assertThat(violations)
                .as("credenciais de administrador versionadas (arquivo, linha e regra; nunca o valor)")
                .isEmpty();
    }

    @Test
    @DisplayName("as variáveis ADMIN_BOOTSTRAP_* do infra/.env.example existem e estão vazias")
    void exampleEnvironmentFileHasEmptyBootstrapVariables() throws IOException {
        Path file = GlossaryFile.load().root().resolve("infra/.env.example");
        List<String> lines = Files.readAllLines(file);
        for (String variable :
                List.of("ADMIN_BOOTSTRAP_NAME", "ADMIN_BOOTSTRAP_EMAIL", "ADMIN_BOOTSTRAP_PASSWORD")) {
            List<String> declarations =
                    lines.stream().filter(line -> line.startsWith(variable + "=")).toList();
            assertThat(declarations).as("declaração de " + variable).hasSize(1);
            assertThat(declarations.getFirst().substring(variable.length() + 1).strip())
                    .as("valor de " + variable + " no .env.example")
                    .isEmpty();
        }
        // nenhuma variável de segredo do administrador tem valor, nem comentada
        for (int i = 0; i < lines.size(); i++) {
            Matcher secret = SECRET_ASSIGNMENT.matcher(lines.get(i).replaceFirst("^#\\s*", ""));
            if (secret.find()) {
                assertThat(secret.group(2).strip())
                        .as("valor da variável da linha " + (i + 1) + " do .env.example")
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("o compose e o application.yaml só repassam as variáveis ADMIN_BOOTSTRAP_*, sem padrão")
    void composeAndConfigurationHaveNoBootstrapDefaults() throws IOException {
        Path root = GlossaryFile.load().root();
        List<String> files =
                List.of(
                        "infra/compose.yaml",
                        "infra/compose.dev.yaml",
                        "infra/compose.prod.yaml",
                        "backend/application/src/main/resources/application.yaml");
        int passed = 0;
        for (String relative : files) {
            Path file = root.resolve(relative);
            if (!Files.exists(file)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher defaulted = BOOTSTRAP_DEFAULT.matcher(lines.get(i));
                while (defaulted.find()) {
                    passed++;
                    assertThat(defaulted.group(1))
                            .as("padrão de ADMIN_BOOTSTRAP_* em " + relative + ":" + (i + 1))
                            .isEmpty();
                }
            }
        }
        assertThat(passed).as("o repasse das variáveis ao backend existe").isGreaterThanOrEqualTo(3);
    }

    // ---------------------------------------------------------------- regras

    /** Texto de produção, infraestrutura e documentação; fora o que não é versionado ou é de teste. */
    private static boolean scannable(String path) {
        if (UNVERSIONED.contains(path) || WORK_TREE.matcher(path).matches()) {
            return false;
        }
        if (TEST_TREE.matcher(path).matches()) {
            return false;
        }
        if (path.startsWith("infra/seed/") && path.endsWith(".json")) {
            return VERSIONED_SEEDS.contains(path);
        }
        if (path.matches("infra/\\.env\\.[^/]+") && !path.equals("infra/.env.example")) {
            return false;
        }
        return true;
    }

    /** Lê o arquivo como UTF-8; devolve nulo para binário ou arquivo grande demais. */
    private static String readText(Path file) throws IOException {
        if (Files.size(file) > 2_000_000) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException binary) {
            return null;
        }
    }

    /** Violações de um arquivo; cada uma cita a linha e a regra, nunca o valor. */
    static List<String> scan(String path, String content) {
        List<String> found = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String where = path + ":" + (i + 1);
            if (ARGON2_HASH.matcher(line).find()
                    || BCRYPT_HASH.matcher(line).find()
                    || PBKDF2_HASH.matcher(line).find()) {
                found.add(where + " [PASSWORD_HASH] hash de senha");
            }
            Matcher bootstrap = BOOTSTRAP_ASSIGNMENT.matcher(line);
            if (bootstrap.find() && literal(bootstrap.group(2))) {
                found.add(where + " [BOOTSTRAP_VALUE] valor literal em ADMIN_BOOTSTRAP_" + bootstrap.group(1));
            }
            Matcher defaulted = BOOTSTRAP_DEFAULT.matcher(line);
            while (defaulted.find()) {
                if (!defaulted.group(1).isEmpty()) {
                    found.add(where + " [BOOTSTRAP_DEFAULT] valor padrão de ADMIN_BOOTSTRAP_*");
                }
            }
            Matcher secret = SECRET_ASSIGNMENT.matcher(line.replaceFirst("^\\s*#\\s*", ""));
            if (secret.find() && literal(secret.group(2))) {
                found.add(where + " [ADMIN_SECRET] valor literal em " + secret.group(1));
            }
            Matcher property = ADMIN_PASSWORD_PROPERTY.matcher(line);
            if (property.find() && literal(property.group(1))) {
                found.add(where + " [BOOTSTRAP_PROPERTY] valor literal na propriedade da senha do primeiro administrador");
            }
        }
        return found;
    }

    /**
     * O valor é literal quando não é vazio, referência de variável, expressão de shell, marcador
     * (entre {@code <>}) nem reticências de exemplo em documentação.
     */
    static boolean literal(String raw) {
        String value = raw.strip();
        if (value.startsWith("#")) {
            return false;
        }
        value = value.replaceAll("^[\"'`]+|[\"'`]+$", "").strip();
        if (value.isEmpty() || value.startsWith("$") || value.startsWith("<") || value.startsWith("{")) {
            return false;
        }
        return !(value.equals("...") || value.equals("…") || value.equals("\\") || value.endsWith("`"));
    }
}
