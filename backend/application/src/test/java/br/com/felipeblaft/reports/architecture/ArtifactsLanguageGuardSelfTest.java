package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.architecture.GlossaryFile.Violation;
import br.com.felipeblaft.reports.architecture.RepositoryWalker.Entry;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Autotestes do guardião de artefatos (feature 003, US2 cenário 2), sem banco: fontes sintéticas
 * com um termo do glossário em tabela, coluna, chave de YAML, variável de ambiente, campo de
 * contrato, código de erro, chave de catálogo, script, nome de workflow, ação de botão, marcador de
 * texto e caminho de arquivo de ignorar; e os casos de exceção e de texto para pessoas, que exigem
 * o contrário.
 *
 * <p>Os termos em português vêm do próprio glossário (equivalente em inglês → palavra portuguesa),
 * então este arquivo não contém literal em português.
 */
class ArtifactsLanguageGuardSelfTest {

    private final GlossaryFile glossary = GlossaryFile.load();
    private final ArtifactScanner scanner = new ArtifactScanner(glossary);

    private String pt(String english) {
        return glossary.portugueseFor(english);
    }

    private static String upper(String word) {
        return word.toUpperCase(Locale.ROOT);
    }

    private void assertFlagged(List<Violation> violations, String english, int line) {
        assertThat(violations)
                .as("violações %s", violations)
                .anySatisfy(
                        violation -> {
                            assertThat(violation.english()).isEqualTo(english);
                            assertThat(violation.line()).isEqualTo(line);
                            assertThat(violation.message()).contains("→ use \"" + english);
                        });
    }

    @Test
    @DisplayName(
            "YAML: chave, valor de uma palavra e comando de workflow; texto para pessoas fica de fora")
    void examinesYaml() {
        String student = pt("student");
        String yaml =
                """
        services:
          %1$s:
            image: node:24-alpine
            description: Texto com espaço sobre o %1$s
        jobs:
          build:
            steps:
              - run: sh infra/scripts/local.sh %2$s backend
        """
                        .formatted(student, pt("tests"));
        List<Violation> found = scanner.scan("infra/compose.yaml", yaml);
        assertFlagged(found, "student", 2);
        assertFlagged(found, "tests", 8);
        assertThat(found).noneMatch(violation -> violation.line() == 4);
    }

    @Test
    @DisplayName("YAML de workflow: o nome do arquivo também é examinado")
    void examinesWorkflowFileName() {
        String name = pt("deploy") + ".yml";
        assertFlagged(
                glossary.violations(
                        ".github/workflows/" + name, 1, "file", name, GlossaryFile.Kind.PATH),
                "deploy",
                1);
    }

    @Test
    @DisplayName("contrato JSON: campo, código de erro e valor de enumeração")
    void examinesContractFields() {
        String student = pt("student");
        String json =
                """
        {
          "properties": {
            "%1$s": {"type": "string", "description": "Texto de ajuda do campo"}
          },
          "enum": ["%2$s_NOT_FOUND", "OK"]
        }
        """
                        .formatted(student, upper(student));
        List<Violation> found = scanner.scan("specs/001/contracts/api.json", json);
        assertFlagged(found, "student", 3);
        assertFlagged(found, "student", 5);
        assertThat(found).noneMatch(violation -> violation.text().contains(" "));
    }

    @Test
    @DisplayName("contrato YAML em specs/**/contracts é varrido; a prosa das specs não")
    void scansContractsButNotSpecProse() {
        assertThat(
                        RepositoryWalker.inArtifactsScope(
                                new Entry("specs/001-x/contracts/api.yaml", false)))
                .isTrue();
        assertThat(RepositoryWalker.inArtifactsScope(new Entry("specs/001-x/spec.md", false)))
                .isFalse();
        assertThat(
                        RepositoryWalker.inArtifactsScope(
                                new Entry("specs/001-x/contracts/notes.md", false)))
                .isFalse();
        String yaml = "paths:\n  /api/" + pt("student") + "s:\n    get:\n      summary: Lista\n";
        assertFlagged(scanner.scan("specs/001-x/contracts/api.yaml", yaml), "student", 2);
    }

    @Test
    @DisplayName(
            "ação de botão v1:<ação>:<n>, rotas e query strings são examinadas palavra a palavra")
    void examinesButtonActionsAndRoutes() {
        String approve = pt("approve");
        String json =
                "{\"data\": \"v1:"
                        + approve
                        + ":7\", \"route\": \"/x?"
                        + pt("search")
                        + "=a&b=1\"}";
        List<Violation> found = scanner.scan("backend/application/src/test/resources/x.json", json);
        assertThat(found).extracting(Violation::english).contains("approve", "search");
    }

    @Test
    @DisplayName(".env.example: nome da variável de ambiente e valor de uma palavra")
    void examinesEnvironmentVariables() {
        String variable = upper(pt("student")) + "_URL";
        String env =
                "# comentário com "
                        + pt("student")
                        + "\n#"
                        + variable
                        + "=http://x\nOTHER_KEY="
                        + pt("simulated")
                        + "\n";
        List<Violation> found = scanner.scan("infra/.env.example", env);
        assertFlagged(found, "student", 2);
        assertFlagged(found, "simulated", 3);
        assertThat(found).noneMatch(violation -> violation.line() == 1);
    }

    @Test
    @DisplayName(".properties: só a chave e os marcadores {nome}; o texto do valor não")
    void examinesCatalogKeysAndPlaceholders() {
        String properties =
                "# comentário\n"
                        + pt("student").toUpperCase(Locale.ROOT)
                        + "_TITLE=Texto para pessoas com {"
                        + pt("minutes")
                        + "} no meio\nOTHER_KEY=Só texto, sem marcador\n";
        List<Violation> found =
                scanner.scan(
                        "backend/application/src/main/resources/messages.properties", properties);
        assertThat(found).extracting(Violation::kind).contains("key", "placeholder");
        assertThat(found).extracting(Violation::english).contains("student", "minutes");
        assertThat(found).noneMatch(violation -> violation.line() == 3);
    }

    @Test
    @DisplayName("script: nomes de função, variável e subcomando; echo e comentário não")
    void examinesShellScripts() {
        String sh =
                "#!/bin/sh\n# "
                        + pt("student")
                        + " em comentário\n"
                        + pt("list")
                        + "_all() {\n  echo \"Texto para pessoas\"\n  "
                        + pt("student")
                        + "=1\n}\n";
        List<Violation> found = scanner.scan("infra/scripts/local.sh", sh);
        assertFlagged(found, "list", 3);
        assertFlagged(found, "student", 5);
        assertThat(found).noneMatch(violation -> violation.line() == 2 || violation.line() == 4);
    }

    @Test
    @DisplayName(
            "SQL V9 em diante: tabela, coluna e literal de uma palavra; comentário e texto não")
    void examinesSql() {
        String sql =
                "-- comentário "
                        + pt("student")
                        + "\ncreate table "
                        + pt("student")
                        + " (id int, "
                        + pt("name")
                        + " text default 'Texto longo');\n";
        List<Violation> found =
                scanner.scan("backend/application/src/main/resources/db/migration/V9__x.sql", sql);
        assertFlagged(found, "student", 2);
        assertFlagged(found, "name", 2);
        assertThat(found).noneMatch(violation -> violation.line() == 1);
    }

    @Test
    @DisplayName("migrações V1 a V8 e as pastas fora do escopo são isentas; V9 não")
    void exemptsImmutableMigrationsAndOutOfScopeFolders() {
        GlossaryFile fresh = GlossaryFile.load();
        String base = "backend/" + pt("application") + "/src/main/resources/db/migration/";
        assertThat(fresh.isSkippedFile(base + "V1__x.sql")).isTrue();
        assertThat(fresh.isSkippedFile(base + "V8__x.sql")).isTrue();
        assertThat(fresh.isSkippedFile(base + "V9__x.sql")).isFalse();
        assertThat(fresh.isSkippedFile("assets/any.txt")).isTrue();
        assertThat(fresh.isSkippedFile(".specify/memory/glossary.json")).isTrue();
        assertThat(fresh.isSkippedFile("specs/001-x/contracts/api.yaml")).isFalse();
        assertThat(fresh.isSkippedFile("specs/001-x/spec.md")).isTrue();
    }

    @Test
    @DisplayName(
            "pom.xml: módulo e artifactId; HTML: classe e expressão; CSS: classe e propriedade")
    void examinesMarkupAndStyles() {
        String student = pt("student");
        List<Violation> pom =
                scanner.scan(
                        "backend/pom.xml",
                        "<project>\n<module>" + student + "</module>\n</project>\n");
        assertFlagged(pom, "student", 2);

        String html =
                "<div class=\""
                        + student
                        + "-card\">\n<p th:text=\"${"
                        + student
                        + ".name}\">Text for people</p></div>";
        List<Violation> page =
                scanner.scan("backend/application/src/main/resources/pdf/t.html", html);
        assertFlagged(page, "student", 1);
        assertFlagged(page, "student", 2);

        String css =
                "."
                        + student
                        + "-card {\n  --"
                        + student
                        + "-gap: 4px;\n  color: var(--"
                        + student
                        + "-gap);\n}\n";
        List<Violation> styles =
                scanner.scan("backend/application/src/main/resources/pdf/s.css", css);
        assertFlagged(styles, "student", 1);
        assertFlagged(styles, "student", 2);
    }

    @Test
    @DisplayName(".gitignore e .dockerignore: caminhos com termos do glossário")
    void examinesIgnoreFiles() {
        String ignore = "# comentário\n" + pt("student") + "/target/\n";
        assertFlagged(scanner.scan(".gitignore", ignore), "student", 2);
    }

    @Test
    @DisplayName("objetos do catálogo do banco: tabela, coluna, índice, constraint e sequência")
    void examinesCatalogObjects() {
        String student = pt("student");
        List<Violation> table = scanner.catalogObject("table", student, student);
        assertThat(table)
                .singleElement()
                .satisfies(
                        violation -> {
                            assertThat(violation.path()).isEqualTo("catalog:table:" + student);
                            assertThat(violation.english()).isEqualTo("student");
                        });
        assertThat(scanner.catalogObject("column", "teacher." + pt("name"), pt("name")))
                .isNotEmpty();
        assertThat(scanner.catalogObject("column", "teacher.created_at", "created_at")).isEmpty();
    }
}
