package br.com.felipeblaft.relatorios.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.arquitetura.GlossaryFile.Kind;
import br.com.felipeblaft.relatorios.arquitetura.GlossaryFile.Violation;
import br.com.felipeblaft.relatorios.arquitetura.RepositoryWalker.Entry;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Autotestes do guardião Java (feature 003, US2 cenário 2): alimentam o guardião com fonte
 * sintética contendo um termo do glossário em cada tipo de artefato Java e exigem a falha com
 * arquivo, termo e equivalente; e com texto para pessoas e exceções, que exigem o contrário. É
 * código de teste: não há ramo de produção para isso.
 *
 * <p>Os termos em português são tirados do próprio glossário (equivalente em inglês → palavra
 * portuguesa), de modo que este arquivo não contém nenhum literal em português e nasce limpo para o
 * guardião.
 */
class LanguageGuardSelfTest {

    private static final String SOURCE_PATH = "backend/module/src/main/java/Sample.java";

    private final GlossaryFile glossary = GlossaryFile.load();
    private final JavaSourceScanner scanner = new JavaSourceScanner(glossary);

    /**
     * Palavra portuguesa do glossário para um equivalente em inglês ("student" → a palavra de
     * aluno).
     */
    private String pt(String english) {
        return glossary.portugueseFor(english);
    }

    private static String capitalize(String word) {
        return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }

    private List<Violation> scan(String source) {
        return scanner.scan(SOURCE_PATH, source);
    }

    private void assertFlagged(List<Violation> violations, String english, int line) {
        assertThat(violations)
                .anySatisfy(
                        violation -> {
                            assertThat(violation.english()).isEqualTo(english);
                            assertThat(violation.line()).isEqualTo(line);
                            assertThat(violation.path()).isEqualTo(SOURCE_PATH);
                            assertThat(violation.message())
                                    .startsWith(SOURCE_PATH + ":" + line + ":")
                                    .contains("\"" + violation.term() + "\"")
                                    .contains("→ use \"" + english);
                        });
    }

    @Test
    @DisplayName(
            "reprova nome de classe, método, campo, variável, parâmetro, constante, record e enum")
    void rejectsEveryKindOfJavaName() {
        String student = pt("student");
        String source =
                """
        class %1$s {
          static final int MAX_%2$s = 1;
          int %3$s;
          void list%1$s(String %3$sParam) {
            int local%1$s = 0;
          }
          record Pair(String %3$s) {}
          enum Kind { %2$s }
        }
        """
                        .formatted(capitalize(student), student.toUpperCase(Locale.ROOT), student);
        List<Violation> violations = scan(source);
        assertFlagged(violations, "student", 1);
        assertFlagged(violations, "student", 2);
        assertFlagged(violations, "student", 3);
        assertFlagged(violations, "student", 4);
        assertFlagged(violations, "student", 5);
        assertFlagged(violations, "student", 7);
        assertFlagged(violations, "student", 8);
    }

    @Test
    @DisplayName("reprova literal de uma palavra, mas aceita texto para pessoas")
    void rejectsOneWordLiteralButAcceptsTextForPeople() {
        String student = pt("student");
        assertFlagged(scan("class A { String x = \"" + student + "\"; }"), "student", 1);
        assertFlagged(scan("class A { String x = \"/api/" + student + "s/{id}\"; }"), "student", 1);
        // texto para pessoas: espaço, acento ou maiúscula inicial isolada
        assertThat(scan("class A { String x = \"o " + student + " chegou\"; }")).isEmpty();
        assertThat(scan("class A { String x = \"" + capitalize(student) + "\"; }")).isEmpty();
        assertThat(scan("class A { String x = \"" + student + "ção\"; }")).isEmpty();
    }

    @Test
    @DisplayName("ignora comentários, javadoc, package e import")
    void ignoresCommentsPackageAndImports() {
        String student = pt("student");
        String source =
                """
        package br.com.exemplo.%1$s;
        import br.com.exemplo.%1$s.Other;
        // %1$s em comentário
        /** %1$s em javadoc */
        /* %1$s em bloco */
        class A {}
        """
                        .formatted(student);
        assertThat(scan(source)).isEmpty();
    }

    @Test
    @DisplayName("examina o JPQL de @Query e o JSON e o SQL de blocos de texto, token a token")
    void examinesEmbeddedCode() {
        String student = pt("student");
        String query =
                "@Query(\"select s from "
                        + capitalize(student)
                        + " s where s."
                        + student
                        + " = :x\") void q();";
        assertFlagged(scan("interface R { " + query + " }"), "student", 1);

        String json = "class A { String j = \"\"\"\n{\"" + student + "\": 1}\n\"\"\"; }";
        assertFlagged(scan(json), "student", 2);

        String sql = "class A { String q = \"\"\"\nselect " + student + " from t\n\"\"\"; }";
        assertFlagged(scan(sql), "student", 2);

        // bloco de texto comum é texto para pessoas
        assertThat(scan("class A { String t = \"\"\"\n" + student + " é bom\n\"\"\"; }")).isEmpty();
    }

    @Test
    @DisplayName("aceita as palavras do professor só na classe das palavras-chave do Telegram")
    void acceptsTeacherKeywordsOnlyInTheKeywordsClass() {
        String approve = pt("approve");
        String source = "class A { String k = \"" + approve + "\"; }";
        String keywordsPath = "backend/module/src/main/java/TelegramKeywords.java";
        assertThat(scanner.scan(keywordsPath, source)).isEmpty();
        assertThat(scanner.scan(SOURCE_PATH, source)).isNotEmpty();
    }

    @Test
    @DisplayName("aceita o nome do PDF mostrado ao usuário só como literal de código Java")
    void acceptsDownloadFileNameOnlyAsJavaLiteral() {
        String fileName = pt("report") + "-" + pt("lesson");
        assertThat(scan("class A { String n = \"" + fileName + "-\"; }")).isEmpty();
        assertThat(scan("class A { String " + fileName.replace('-', '_') + " = null; }"))
                .isNotEmpty();
    }

    @Test
    @DisplayName("reprova nome de pasta e de arquivo, uma vez na própria pasta")
    void rejectsFileAndDirectoryNames(@TempDir Path root) throws IOException {
        String student = pt("student");
        Path directory = root.resolve("backend/module/src/main/java").resolve(student);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(capitalize(student) + ".java"), "class Clean {}");

        List<Entry> entries = new RepositoryWalker(root).walk();
        List<Violation> found =
                entries.stream()
                        .filter(RepositoryWalker::inJavaScope)
                        .flatMap(
                                entry ->
                                        glossary
                                                .violations(
                                                        entry.path(),
                                                        1,
                                                        "name",
                                                        entry.name(),
                                                        Kind.PATH)
                                                .stream())
                        .toList();
        assertThat(found)
                .extracting(Violation::path)
                .contains(
                        "backend/module/src/main/java/" + student,
                        "backend/module/src/main/java/"
                                + student
                                + "/"
                                + capitalize(student)
                                + ".java");
        // a pasta é reportada na pasta; o arquivo, só pelo próprio nome
        assertThat(found).noneMatch(violation -> violation.path().endsWith("/java"));
    }

    @Test
    @DisplayName("exclusões temporárias: arquivo, termo retido, arquivo novo e exclusão morta")
    void temporaryExclusionsBehave() throws IOException {
        String student = pt("student");
        String exclusions =
                """
        {"files": [{"path": "old/Existing.java", "slice": 2}, {"path": "old/Gone.java", "slice": 2}],
         "terms": [{"path": "old/Held.java", "term": "%s", "slice": 4}]}
        """
                        .formatted(student);
        String glossaryJson = Files.readString(glossary.root().resolve(GlossaryFile.GLOSSARY));
        GlossaryFile withExclusions =
                GlossaryFile.fromJson(glossary.root(), glossaryJson, exclusions);

        Violation existing =
                new Violation("old/Existing.java", 3, "identifier", "x", student, "student", "");
        Violation held =
                new Violation("old/Held.java", 1, "identifier", "x", student, "student", "");
        Violation heldOther =
                new Violation("old/Held.java", 2, "identifier", "y", pt("teacher"), "teacher", "");
        Violation fresh =
                new Violation("new/Fresh.java", 1, "identifier", "z", student, "student", "");

        GlossaryFile.Outcome outcome =
                withExclusions.applyExclusions(
                        List.of(existing, held, heldOther, fresh), path -> true);

        assertThat(outcome.remaining()).containsExactlyInAnyOrder(heldOther, fresh);
        assertThat(outcome.deadExclusions()).containsExactly("old/Gone.java");
    }

    @Test
    @DisplayName("falha reportando todas as ocorrências, com arquivo:linha, termo e equivalente")
    void failureListsEveryOccurrence() {
        Violation first =
                new Violation(
                        "a/A.java", 3, "identifier", "listStudents", "student", "student", "");
        Violation second = new Violation("b/B.java", 9, "literal", "x", "teacher", "teacher", "");
        assertThatThrownBy(() -> glossary.enforce("java", List.of(first, second), path -> false))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "a/A.java:3: \"listStudents\" contém \"student\" → use \"student\"")
                .hasMessageContaining("b/B.java:9:");
    }

    @Test
    @DisplayName("recusa exceção sem motivo e sem categoria do Princípio VI")
    void rejectsExceptionWithoutReasonOrCategory() throws IOException {
        String noReason =
                "{\"words\": {}, \"exceptions\": [{\"id\": \"x\", \"mode\": \"term\", \"constitutionCategory\": \"c\"}]}";
        String noCategory =
                "{\"words\": {}, \"exceptions\": [{\"id\": \"x\", \"mode\": \"term\", \"reason\": \"r\"}]}";
        assertThatThrownBy(() -> GlossaryFile.fromJson(Path.of("."), noReason, null))
                .hasMessageContaining("sem motivo");
        assertThatThrownBy(() -> GlossaryFile.fromJson(Path.of("."), noCategory, null))
                .hasMessageContaining("sem categoria");
    }

    @Test
    @DisplayName("aponta exceção morta: documentada, mas sem uso")
    void reportsDeadExceptions() {
        GlossaryFile fresh = GlossaryFile.load();
        assertThat(fresh.deadExceptions("java")).isNotEmpty();
        fresh.examine("br.com.felipeblaft.x", "backend/x/Sample.java", Kind.IDENTIFIER);
        assertThat(fresh.deadExceptions("java")).doesNotContain("org-name");
    }

    @Test
    @DisplayName(
            "reprova nomes do bytecode: pacote, classe, método, campo, constante, record e valor de anotação")
    void rejectsBytecodeNames(@TempDir Path output) throws IOException {
        String student = pt("student");
        String domain = pt("domain");
        String source =
                """
        package demo.%1$s;
        @interface Marker { String value(); }
        public class %2$s {
          @Marker("%3$s") int %3$sField;
          void list%2$s() {}
          enum Kind { %4$s }
          record Pair(String %3$s) {}
        }
        """
                        .formatted(
                                domain,
                                capitalize(student),
                                student,
                                student.toUpperCase(Locale.ROOT));
        compile(output, "demo." + domain + "." + capitalize(student), source);

        JavaClasses classes = new ClassFileImporter().importPath(output);
        List<Violation> found =
                new BytecodeNameScanner(glossary)
                        .scan(classes, type -> "classpath:" + type.getName());
        assertThat(found)
                .extracting(Violation::kind)
                .contains("package", "class", "method", "field", "annotation-value");
        assertThat(found).extracting(Violation::english).contains("domain", "student");
    }

    private static void compile(Path output, String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject file =
                new SimpleJavaFileObject(
                        URI.create("string:///" + className.replace('.', '/') + ".java"),
                        JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return source;
                    }
                };
        Boolean ok =
                compiler.getTask(
                                null,
                                null,
                                null,
                                List.of("-d", output.toString()),
                                null,
                                List.of(file))
                        .call();
        assertThat(ok).isTrue();
    }
}
