package br.com.felipeblaft.reports.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Percorre o repositório (raízes explícitas, sem {@code target}, {@code node_modules} nem {@code
 * .git}) e define o que cada guardião examina. Nomes de pasta são examinados uma vez, na própria
 * pasta; nomes de arquivo, no próprio arquivo (a falha de uma pasta não se repete em cada arquivo
 * dentro dela).
 */
public final class RepositoryWalker {

    /** Uma entrada do repositório: caminho relativo com barras normais e se é pasta. */
    public record Entry(String path, boolean directory) {

        public String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    private static final Set<String> IGNORED_DIRECTORIES =
            Set.of(
                    ".git",
                    "node_modules",
                    "target",
                    "dist",
                    "build",
                    "playwright-report",
                    "test-results",
                    ".idea",
                    ".vscode");

    private static final Pattern JAVA_TREE =
            Pattern.compile("backend/[^/]+/src/(main|test)/java(/.*)?");
    private static final Pattern MODULE_DIRECTORY = Pattern.compile("backend/[^/.]+");
    private static final Pattern BACKEND_RESOURCES =
            Pattern.compile("backend/[^/]+/src/(main|test)/resources(/.*)?");
    private static final Pattern BACKEND_BUILD_FILES =
            Pattern.compile(
                    "backend/(pom\\.xml|\\.dockerignore|[^/]+/(pom\\.xml|Dockerfile|docker-entrypoint\\.sh))");
    private static final Pattern ROOT_FILES =
            Pattern.compile("\\.(gitignore|dockerignore|gitattributes|editorconfig)");
    private static final Pattern FRONTEND_ARTIFACTS =
            Pattern.compile(
                    "frontend/(package\\.json|Dockerfile|index\\.html|\\.prettier[^/]*|src/.*\\.json)");
    private static final Pattern CONTRACTS =
            Pattern.compile("specs/.*/contracts/[^/]+\\.(yaml|json)");
    private static final Pattern FRONTEND_CODE =
            Pattern.compile(
                    "frontend/(src|tests|scripts|public)(/.*)?|frontend/[^/]+\\.(ts|js|mjs|cjs)");

    private final Path root;

    public RepositoryWalker(Path root) {
        this.root = root;
    }

    /** Todas as pastas e arquivos do repositório, em ordem estável. */
    public List<Entry> walk() {
        List<Entry> entries = new ArrayList<>();
        try {
            walk(root, entries);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao percorrer " + root, e);
        }
        return entries;
    }

    private void walk(Path directory, List<Entry> entries) throws IOException {
        List<Path> children;
        try (Stream<Path> stream = Files.list(directory)) {
            children = stream.sorted().toList();
        }
        for (Path child : children) {
            String name = child.getFileName().toString();
            String relative = root.relativize(child).toString().replace('\\', '/');
            if (Files.isDirectory(child)) {
                if (IGNORED_DIRECTORIES.contains(name)) {
                    continue;
                }
                entries.add(new Entry(relative, true));
                walk(child, entries);
            } else if (Files.isRegularFile(child)) {
                entries.add(new Entry(relative, false));
            }
        }
    }

    public String read(Entry entry) {
        try {
            return Files.readString(root.resolve(entry.path()));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + entry.path(), e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Escopos (o que cada guardião examina)
    // ---------------------------------------------------------------------------------------------

    /** Guardião Java: módulos do backend e árvores src/{main,test}/java. */
    public static boolean inJavaScope(Entry entry) {
        String path = entry.path();
        if (entry.directory()) {
            return MODULE_DIRECTORY.matcher(path).matches() || JAVA_TREE.matcher(path).matches();
        }
        return path.endsWith(".java") && JAVA_TREE.matcher(path).matches();
    }

    /**
     * Guardião de artefatos: infra, workflows, recursos e arquivos de build/configuração, contratos
     * em specs.
     */
    public static boolean inArtifactsScope(Entry entry) {
        String path = entry.path();
        if (path.equals("infra")
                || path.startsWith("infra/")
                || path.equals(".github")
                || path.startsWith(".github/")) {
            return true;
        }
        if (BACKEND_RESOURCES.matcher(path).matches()) {
            return true;
        }
        if (entry.directory()) {
            return false;
        }
        return BACKEND_BUILD_FILES.matcher(path).matches()
                || ROOT_FILES.matcher(path).matches()
                || FRONTEND_ARTIFACTS.matcher(path).matches()
                || CONTRACTS.matcher(path).matches();
    }

    /** Quem é dono de uma exclusão temporária (para a regra de "exclusão morta"). */
    public static boolean ownedByJavaGuard(String path) {
        return !path.startsWith("catalog:")
                && (MODULE_DIRECTORY.matcher(path).matches() || JAVA_TREE.matcher(path).matches());
    }

    public static boolean ownedByArtifactsGuard(String path) {
        if (path.startsWith("catalog:")) {
            return true;
        }
        return inArtifactsScope(new Entry(path, false)) && !JAVA_TREE.matcher(path).matches()
                || inArtifactsScope(new Entry(path, true)) && !JAVA_TREE.matcher(path).matches();
    }

    public static boolean ownedByTypescriptGuard(String path) {
        return FRONTEND_CODE.matcher(path).matches() && !FRONTEND_ARTIFACTS.matcher(path).matches();
    }
}
