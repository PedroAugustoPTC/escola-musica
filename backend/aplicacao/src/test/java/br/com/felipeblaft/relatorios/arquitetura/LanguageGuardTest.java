package br.com.felipeblaft.relatorios.arquitetura;

import br.com.felipeblaft.relatorios.arquitetura.GlossaryFile.Kind;
import br.com.felipeblaft.relatorios.arquitetura.GlossaryFile.Violation;
import br.com.felipeblaft.relatorios.arquitetura.RepositoryWalker.Entry;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardião de idioma do código Java (feature 003, FR-019 a FR-023): pacotes, tipos, métodos,
 * campos, variáveis, parâmetros, constantes, componentes de {@code record}, literais de uma
 * palavra, SQL/JPQL embutido, e nomes de arquivos e pastas dos módulos do backend não podem conter
 * termos do glossário em português ({@code .specify/memory/glossary.json}). O que ainda não migrou
 * fica em {@code glossary-temporary-exclusions.json} (só existe na branch da feature).
 *
 * <p>Modo relatório: {@code GUARD_REPORT=1} grava {@code target/guard-violations-java.json} em vez
 * de reprovar.
 */
class LanguageGuardTest {

    private static final String GUARD = "java";

    @Test
    @DisplayName("o código Java usa só nomes em inglês (fora das exceções documentadas)")
    void javaCodeUsesOnlyEnglishNames() {
        GlossaryFile glossary = GlossaryFile.load();
        RepositoryWalker walker = new RepositoryWalker(glossary.root());
        JavaSourceScanner scanner = new JavaSourceScanner(glossary);
        List<Violation> found = new ArrayList<>();
        Map<String, String> sourceByType = new HashMap<>();

        for (Entry entry : walker.walk()) {
            if (!RepositoryWalker.inJavaScope(entry) || glossary.isSkippedFile(entry.path())) {
                continue;
            }
            found.addAll(
                    glossary.violations(
                            entry.path(),
                            1,
                            entry.directory() ? "directory" : "file",
                            entry.name(),
                            Kind.PATH));
            if (!entry.directory()) {
                found.addAll(scanner.scan(entry.path(), walker.read(entry)));
                int marker = entry.path().indexOf("/java/");
                sourceByType.put(entry.path().substring(marker + "/java/".length()), entry.path());
            }
        }

        // Nomes do bytecode (ArchUnit): só acrescenta o que a varredura de fonte ainda não apontou
        // no arquivo.
        Set<String> alreadyFound = new HashSet<>();
        found.forEach(
                violation -> alreadyFound.add(violation.path() + "\u0000" + violation.term()));
        List<Violation> fromBytecode =
                new BytecodeNameScanner(glossary)
                        .scan(
                                new ClassFileImporter().importPackages("br.com.felipeblaft"),
                                type -> sourcePath(type, sourceByType));
        for (Violation violation : fromBytecode) {
            if (alreadyFound.add(violation.path() + "\u0000" + violation.term())) {
                found.add(violation);
            }
        }

        glossary.conclude(
                GUARD,
                Path.of("target", "guard-violations-java.json"),
                found,
                RepositoryWalker::ownedByJavaGuard);
    }

    private static String sourcePath(JavaClass type, Map<String, String> sourceByType) {
        String name = type.getName();
        int nested = name.indexOf('$');
        String top = nested < 0 ? name : name.substring(0, nested);
        String key = top.replace('.', '/') + ".java";
        return sourceByType.getOrDefault(key, "classpath:" + top);
    }
}
