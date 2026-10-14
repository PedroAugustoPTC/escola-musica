package br.com.felipeblaft.reports.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Carrega o glossário de idioma (.specify/memory/glossary.json) e as exclusões temporárias
 * (.specify/memory/glossary-temporary-exclusions.json) e aplica as regras comuns aos guardiões
 * (feature 003, FR-016 a FR-023 e FR-035; research.md R-05).
 *
 * <p>Casamento: o texto é dividido por camelCase, PascalCase, snake_case, kebab-case, ponto, barra
 * e também por {@code : = ? & ;}; cada palavra é comparada sem acento, e também sem {@code s} e sem
 * {@code es} finais. Conectivos (valor entre parênteses) não geram termo próprio.
 */
public final class GlossaryFile {

    public static final String GLOSSARY = ".specify/memory/glossary.json";
    public static final String EXCLUSIONS = ".specify/memory/glossary-temporary-exclusions.json";

    /** Onde um texto foi encontrado; "literal" restringe as exceções que valem só para literais. */
    public enum Kind {
        IDENTIFIER,
        LITERAL,
        PATH
    }

    /** Um termo do glossário encontrado num texto. */
    public record Finding(String word, String term, String english, String hint) {}

    /** Uma violação: onde, o texto inteiro, o termo e o equivalente esperado. */
    public record Violation(
            String path,
            int line,
            String kind,
            String text,
            String term,
            String english,
            String hint) {

        public String message() {
            String suggestion = hint.isEmpty() ? english : english + hint;
            return "%s:%d: \"%s\" contém \"%s\" → use \"%s\""
                    .formatted(path, line, text, term, suggestion);
        }
    }

    /** Regra de exceção documentada (mode: term, skip-file ou builtin). */
    static final class ExceptionRule {
        final String id;
        final String mode;
        final boolean literalOnly;
        final List<String> terms;
        final List<Pattern> paths;
        final List<Pattern> pathsExcept;
        final Set<String> appliesTo;
        int hits;

        ExceptionRule(Map<String, Object> data) {
            this.id = String.valueOf(data.get("id"));
            Object reason = data.get("reason");
            Object category = data.get("constitutionCategory");
            if (reason == null || String.valueOf(reason).isBlank()) {
                throw new IllegalStateException(
                        "Exceção \"" + id + "\" do glossário sem motivo (reason).");
            }
            if (category == null || String.valueOf(category).isBlank()) {
                throw new IllegalStateException(
                        "Exceção \""
                                + id
                                + "\" do glossário sem categoria do Princípio VI (constitutionCategory).");
            }
            Object declaredMode = data.get("mode");
            if (declaredMode == null) {
                throw new IllegalStateException("Exceção \"" + id + "\" do glossário sem mode.");
            }
            this.mode = String.valueOf(declaredMode);
            this.literalOnly = "literal".equals(data.get("where"));
            this.terms =
                    strings(data.get("terms")).stream()
                            .map(term -> fold(term).toLowerCase(java.util.Locale.ROOT))
                            .toList();
            this.paths = strings(data.get("paths")).stream().map(GlossaryFile::glob).toList();
            this.pathsExcept =
                    strings(data.get("pathsExcept")).stream().map(GlossaryFile::glob).toList();
            this.appliesTo = new HashSet<>(strings(data.get("appliesTo")));
        }

        boolean coversPath(String path) {
            boolean in = paths.isEmpty() || paths.stream().anyMatch(p -> p.matcher(path).matches());
            return in && pathsExcept.stream().noneMatch(p -> p.matcher(path).matches());
        }
    }

    private static final Pattern CAMEL_LOWER_UPPER = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern CAMEL_ACRONYM = Pattern.compile("([A-Z]+)([A-Z][a-z])");
    private static final Pattern LETTERS = Pattern.compile("[A-Za-z]+");
    private static final Pattern CAPITALIZED_WORD = Pattern.compile("[A-Z][a-z]+[.!?:,;]*");

    private final Path root;
    private final Map<String, String> words;
    private final Map<String, List<String>> contextual = new HashMap<>();
    private final List<ExceptionRule> exceptions = new ArrayList<>();
    private final Map<String, Integer> excludedFiles = new TreeMap<>();
    private final Set<String> excludedTerms = new HashSet<>();
    private final ExceptionRule textForPeople;

    private GlossaryFile(Path root, Map<String, Object> glossary, Map<String, Object> exclusions) {
        this.root = root;
        this.words = new LinkedHashMap<>();
        asMap(glossary.get("words")).forEach((key, value) -> words.put(key, String.valueOf(value)));
        for (Object item : asList(glossary.get("contextual"))) {
            Map<String, Object> entry = asMap(item);
            contextual
                    .computeIfAbsent(String.valueOf(entry.get("pt")), key -> new ArrayList<>())
                    .add(String.valueOf(entry.get("en")));
        }
        for (Object item : asList(glossary.get("exceptions"))) {
            exceptions.add(new ExceptionRule(asMap(item)));
        }
        this.textForPeople =
                exceptions.stream()
                        .filter(rule -> "builtin".equals(rule.mode))
                        .findFirst()
                        .orElse(null);
        if (exclusions != null) {
            for (Object item : asList(exclusions.get("files"))) {
                Map<String, Object> entry = asMap(item);
                excludedFiles.put(
                        String.valueOf(entry.get("path")),
                        ((Number) entry.getOrDefault("slice", 0)).intValue());
            }
            for (Object item : asList(exclusions.get("terms"))) {
                Map<String, Object> entry = asMap(item);
                excludedTerms.add(entry.get("path") + "\u0000" + entry.get("term"));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Carga
    // ---------------------------------------------------------------------------------------------

    /**
     * Acha a raiz do repositório: GUARD_ROOT, a propriedade guard.root ou o primeiro ancestral com
     * o glossário.
     */
    public static Path findRoot() {
        String configured = System.getProperty("guard.root", System.getenv("GUARD_ROOT"));
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path work = Path.of("/work");
        if (Files.exists(work.resolve(GLOSSARY))) {
            return work;
        }
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(GLOSSARY))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "Glossário não encontrado: defina GUARD_ROOT ou rode dentro do repositório ("
                        + GLOSSARY
                        + ").");
    }

    public static GlossaryFile load() {
        return load(findRoot());
    }

    public static GlossaryFile load(Path root) {
        try {
            Map<String, Object> glossary = parse(Files.readString(root.resolve(GLOSSARY)));
            Path exclusionsPath = root.resolve(EXCLUSIONS);
            Map<String, Object> exclusions =
                    Files.exists(exclusionsPath) ? parse(Files.readString(exclusionsPath)) : null;
            return new GlossaryFile(root, glossary, exclusions);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler o glossário em " + root, e);
        }
    }

    /** Para os autotestes: glossário e exclusões fornecidos em texto JSON. */
    public static GlossaryFile fromJson(Path root, String glossaryJson, String exclusionsJson) {
        return new GlossaryFile(
                root, parse(glossaryJson), exclusionsJson == null ? null : parse(exclusionsJson));
    }

    private static Map<String, Object> parse(String json) {
        // JSON é um subconjunto de YAML: o SnakeYAML (já no classpath do Spring Boot) basta.
        return asMap(new Yaml().load(json));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    private static List<String> strings(Object value) {
        return asList(value).stream().map(String::valueOf).toList();
    }

    public Path root() {
        return root;
    }

    public Map<String, String> words() {
        return words;
    }

    public boolean hasExclusions() {
        return !excludedFiles.isEmpty() || !excludedTerms.isEmpty();
    }

    /** Equivalente em inglês de uma palavra, na forma que o glossário traz (para os autotestes). */
    public String englishOf(String portugueseWord) {
        return words.get(portugueseWord);
    }

    /**
     * Palavra portuguesa cujo equivalente é {@code english} (a primeira do glossário), sem repetir
     * o termo no código.
     */
    public String portugueseFor(String english) {
        return words.entrySet().stream()
                .filter(entry -> entry.getValue().equals(english))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Glossário sem equivalente de \"" + english + "\""));
    }

    // ---------------------------------------------------------------------------------------------
    // Palavras
    // ---------------------------------------------------------------------------------------------

    public static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Divide em palavras minúsculas e sem acento. */
    public static List<String> splitWords(String text) {
        String spaced = fold(text);
        spaced = CAMEL_LOWER_UPPER.matcher(spaced).replaceAll("$1 $2");
        spaced = CAMEL_ACRONYM.matcher(spaced).replaceAll("$1 $2");
        List<String> result = new ArrayList<>();
        Matcher matcher = LETTERS.matcher(spaced);
        while (matcher.find()) {
            result.add(matcher.group().toLowerCase());
        }
        return result;
    }

    private Finding lookup(String word) {
        List<String> candidates = new ArrayList<>(3);
        candidates.add(word);
        if (word.endsWith("s") && word.length() > 2) {
            candidates.add(word.substring(0, word.length() - 1));
        }
        if (word.endsWith("es") && word.length() > 3) {
            candidates.add(word.substring(0, word.length() - 2));
        }
        for (String candidate : candidates) {
            String english = words.get(candidate);
            if (english != null && !english.startsWith("(")) {
                List<String> alternatives = contextual.getOrDefault(candidate, List.of());
                String hint =
                        alternatives.size() > 1
                                ? " (ou \""
                                        + String.join(
                                                "\", \"",
                                                alternatives.stream()
                                                        .filter(en -> !en.equals(english))
                                                        .toList())
                                        + "\", conforme o contexto)"
                                : "";
                return new Finding(word, candidate, english, hint);
            }
        }
        return null;
    }

    /**
     * Examina um texto (identificador, literal de código ou nome de arquivo/pasta) e devolve os
     * termos do glossário encontrados, depois de aplicar as exceções de termo que valem para o
     * caminho.
     */
    public List<Finding> examine(String text, String path, Kind kind) {
        String working = fold(text);
        String lowered = working.toLowerCase();
        StringBuilder masked = new StringBuilder(working);
        for (ExceptionRule rule : exceptions) {
            if (!"term".equals(rule.mode) || !rule.coversPath(path)) {
                continue;
            }
            if (rule.literalOnly && kind != Kind.LITERAL) {
                continue;
            }
            for (String term : rule.terms) {
                int from = 0;
                int at;
                while ((at = lowered.indexOf(term, from)) >= 0) {
                    rule.hits++;
                    for (int i = at; i < at + term.length() && i < masked.length(); i++) {
                        masked.setCharAt(i, ' ');
                    }
                    from = at + term.length();
                }
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (String word : splitWords(masked.toString())) {
            Finding finding = lookup(word);
            if (finding != null) {
                findings.add(finding);
            }
        }
        return findings;
    }

    /** Examina e devolve as violações já formatadas (uma por termo encontrado). */
    public List<Violation> violations(String path, int line, String kind, String text, Kind where) {
        List<Violation> result = new ArrayList<>();
        for (Finding finding : examine(text, path, where)) {
            result.add(
                    new Violation(
                            path,
                            line,
                            kind,
                            text,
                            finding.term(),
                            finding.english(),
                            finding.hint()));
        }
        return result;
    }

    /**
     * Um literal é "texto para pessoas" (FR-023) quando tem espaço, letra acentuada ou é uma única
     * palavra iniciada em maiúscula ("Carregando"). Os demais são código (chaves, rotas, valores).
     */
    public boolean isTextForPeople(String literal) {
        boolean text =
                literal.chars().anyMatch(Character::isWhitespace)
                        || literal.chars().anyMatch(c -> c > 127)
                        || CAPITALIZED_WORD.matcher(literal).matches();
        if (text && textForPeople != null) {
            textForPeople.hits++;
        }
        return text;
    }

    // ---------------------------------------------------------------------------------------------
    // Exceções por arquivo e exclusões temporárias
    // ---------------------------------------------------------------------------------------------

    /** O arquivo (nome e conteúdo) está isento por uma exceção skip-file? */
    public boolean isSkippedFile(String path) {
        boolean skipped = false;
        for (ExceptionRule rule : exceptions) {
            if ("skip-file".equals(rule.mode) && !rule.paths.isEmpty() && rule.coversPath(path)) {
                rule.hits++;
                skipped = true;
            }
        }
        return skipped;
    }

    /** Exceções que este guardião deveria ter usado e não usou (exceção morta). */
    public List<String> deadExceptions(String guard) {
        return exceptions.stream()
                .filter(rule -> rule.appliesTo.contains(guard) && rule.hits == 0)
                .map(rule -> rule.id)
                .sorted()
                .toList();
    }

    /** Resultado da aplicação das exclusões temporárias a uma lista de violações. */
    public record Outcome(List<Violation> remaining, List<String> deadExclusions) {}

    /**
     * Aplica as exclusões temporárias. {@code ownsPath} diz quais entradas pertencem a este
     * guardião; uma entrada dele que não casa com nenhuma violação é "exclusão morta" e deve ser
     * regerada.
     */
    public Outcome applyExclusions(
            Collection<Violation> found, java.util.function.Predicate<String> ownsPath) {
        Set<String> usedFiles = new HashSet<>();
        Set<String> usedTerms = new HashSet<>();
        List<Violation> remaining = new ArrayList<>();
        for (Violation violation : found) {
            if (excludedFiles.containsKey(violation.path())) {
                usedFiles.add(violation.path());
            } else if (excludedTerms.contains(violation.path() + "\u0000" + violation.term())) {
                usedTerms.add(violation.path() + "\u0000" + violation.term());
            } else {
                remaining.add(violation);
            }
        }
        List<String> dead = new ArrayList<>();
        excludedFiles.keySet().stream()
                .filter(ownsPath)
                .filter(path -> !usedFiles.contains(path))
                .forEach(path -> dead.add(path));
        excludedTerms.stream()
                .filter(key -> ownsPath.test(key.substring(0, key.indexOf('\u0000'))))
                .filter(key -> !usedTerms.contains(key))
                .forEach(key -> dead.add(key.replace('\u0000', ' ')));
        remaining.sort(
                Comparator.comparing(Violation::path)
                        .thenComparingInt(Violation::line)
                        .thenComparing(Violation::term));
        return new Outcome(remaining, dead.stream().sorted().toList());
    }

    // ---------------------------------------------------------------------------------------------
    // Relatório e falha
    // ---------------------------------------------------------------------------------------------

    public static boolean reportMode() {
        return "1".equals(System.getenv("GUARD_REPORT"))
                || "1".equals(System.getProperty("guard.report"));
    }

    /** Grava todas as violações (antes das exclusões) em JSON, para o gen-exclusions.mjs. */
    public static void writeReport(Path file, Collection<Violation> all) throws IOException {
        // Agrupa por (caminho, termo, texto): o relatório só precisa da primeira linha e da
        // contagem.
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Violation> firsts = new LinkedHashMap<>();
        for (Violation violation : all) {
            String key =
                    violation.path() + "\u0000" + violation.term() + "\u0000" + violation.text();
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            firsts.putIfAbsent(key, violation);
        }
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (Map.Entry<String, Violation> entry : firsts.entrySet()) {
            Violation violation = entry.getValue();
            json.append(first ? "  " : ",\n  ")
                    .append("{\"path\": ")
                    .append(quote(violation.path()))
                    .append(", \"line\": ")
                    .append(violation.line())
                    .append(", \"kind\": ")
                    .append(quote(violation.kind()))
                    .append(", \"text\": ")
                    .append(quote(violation.text()))
                    .append(", \"term\": ")
                    .append(quote(violation.term()))
                    .append(", \"suggestion\": ")
                    .append(quote(violation.english()))
                    .append(", \"count\": ")
                    .append(counts.get(entry.getKey())[0])
                    .append('}');
            first = false;
        }
        json.append("\n]\n");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, json.toString());
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * Fecha a execução de um guardião: no modo relatório grava o JSON e não reprova; senão, falha
     * listando TODAS as ocorrências (FR-021), as exclusões mortas e as exceções mortas.
     */
    public void conclude(
            String guard,
            Path reportFile,
            List<Violation> all,
            java.util.function.Predicate<String> ownsPath) {
        if (reportMode()) {
            try {
                writeReport(reportFile, all);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return;
        }
        enforce(guard, all, ownsPath);
    }

    /**
     * Reprova (AssertionError) listando todos os problemas; separado de {@code conclude} para os
     * autotestes.
     */
    void enforce(String guard, List<Violation> all, java.util.function.Predicate<String> ownsPath) {
        Outcome outcome = applyExclusions(all, ownsPath);
        List<String> problems = new ArrayList<>();
        outcome.remaining().forEach(violation -> problems.add(violation.message()));
        outcome.deadExclusions()
                .forEach(
                        entry ->
                                problems.add(
                                        "exclusão temporária morta (não casa com nada): " + entry));
        deadExceptions(guard)
                .forEach(
                        id ->
                                problems.add(
                                        "exceção do glossário morta (não casa com nada): " + id));
        if (!problems.isEmpty()) {
            throw new AssertionError(
                    "Guardião de idioma ("
                            + guard
                            + "): "
                            + problems.size()
                            + " problema(s)\n  "
                            + String.join("\n  ", problems));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Glob
    // ---------------------------------------------------------------------------------------------

    /** Converte um glob de caminho (com ** e *) em expressão regular. */
    static Pattern glob(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    boolean slash = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                    regex.append(slash ? "(?:.*/)?" : ".*");
                    i += slash ? 3 : 2;
                    continue;
                }
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (c == '[') {
                int end = glob.indexOf(']', i);
                regex.append(glob, i, end + 1);
                i = end;
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
            i++;
        }
        return Pattern.compile(regex.toString());
    }
}
