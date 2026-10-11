package br.com.felipeblaft.relatorios.arquitetura;

import br.com.felipeblaft.relatorios.arquitetura.GlossaryFile.Kind;
import br.com.felipeblaft.relatorios.arquitetura.GlossaryFile.Violation;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Examina artefatos de dados e configuração (YAML, JSON, .properties, .env, scripts, Dockerfile,
 * XML, HTML, CSS e SQL) do ponto de vista do guardião de idioma (R-05): chaves, nomes e literais de
 * uma palavra são código; valores com espaço, acento ou iniciados em maiúscula isolada são texto
 * para pessoas (FR-023) e não entram.
 */
public final class ArtifactScanner {

    /**
     * Chaves de YAML cujo valor é um comando de shell: as palavras do comando contam, mesmo com
     * espaços.
     */
    private static final Set<String> SHELL_KEYS =
            Set.of("run", "command", "entrypoint", "test", "cmd", "script");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)\\}");
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TAG =
            Pattern.compile("<(/?)([A-Za-z][\\w:.-]*)((?:\\s+[^>]*?)?)/?>", Pattern.DOTALL);
    private static final Pattern ATTRIBUTE =
            Pattern.compile(
                    "([A-Za-z_:@][\\w:.@-]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'))?",
                    Pattern.DOTALL);
    private static final Pattern EXPRESSION = Pattern.compile("[$*#@]\\{([^}]*)}");
    private static final Pattern TEXT_NODE = Pattern.compile(">([^<]+)<");
    private static final Pattern CSS_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern CSS_CODE =
            Pattern.compile("var\\(\\s*(--[\\w-]+)|url\\(\\s*['\"]?([^'\")\\s]+)|(--[\\w-]+)\\s*:");
    private static final Pattern SQL_TOKEN =
            Pattern.compile("'((?:[^']|'')*)'|\"([^\"]+)\"|[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern STYLE_BLOCK =
            Pattern.compile("<style[^>]*>(.*?)</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final GlossaryFile glossary;

    public ArtifactScanner(GlossaryFile glossary) {
        this.glossary = glossary;
    }

    /**
     * Tipos de arquivo que este examinador entende (os demais, como imagens e fontes, só têm o nome
     * examinado).
     */
    public static boolean supports(String name) {
        return name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.endsWith(".json")
                || name.endsWith(".properties")
                || name.equals(".env.example")
                || name.endsWith(".sh")
                || name.equals("Dockerfile")
                || name.equals("Caddyfile")
                || name.startsWith(".") && name.endsWith("ignore")
                || name.endsWith(".xml")
                || name.endsWith(".html")
                || name.endsWith(".css")
                || name.endsWith(".sql");
    }

    /**
     * Objeto do catálogo do banco (tabela, coluna, índice, constraint, sequência): {@code identity}
     * é o id na exclusão.
     */
    public List<Violation> catalogObject(String kind, String identity, String name) {
        return glossary.violations(
                "catalog:" + kind + ":" + identity, 0, "catalog-" + kind, name, Kind.IDENTIFIER);
    }

    public List<Violation> scan(String path, String content) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return yaml(path, content);
        }
        if (name.endsWith(".json")) {
            List<Violation> found = json(path, content);
            if (name.equals("package.json")) {
                found.addAll(packageScripts(path, content));
            }
            return found;
        }
        if (name.endsWith(".properties")) {
            return properties(path, content);
        }
        if (name.equals(".env.example")) {
            return env(path, content);
        }
        if (name.endsWith(".sh")
                || name.equals("Dockerfile")
                || name.equals("Caddyfile")
                || name.startsWith(".") && name.endsWith("ignore")) {
            return shellLike(path, content);
        }
        if (name.endsWith(".xml")) {
            return markup(path, content, true);
        }
        if (name.endsWith(".html")) {
            return markup(path, content, false);
        }
        if (name.endsWith(".css")) {
            return css(path, content, 1);
        }
        if (name.endsWith(".sql")) {
            return sql(path, content);
        }
        return new ArrayList<>();
    }

    // ---------------------------------------------------------------------------------------------
    // YAML
    // ---------------------------------------------------------------------------------------------

    List<Violation> yaml(String path, String content) {
        List<Violation> found = new ArrayList<>();
        try {
            for (Node document : new Yaml().composeAll(new StringReader(content))) {
                walkYaml(document, path, false, found);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("YAML inválido para o guardião: " + path, e);
        }
        return found;
    }

    private void walkYaml(Node node, String path, boolean shell, List<Violation> found) {
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                String key =
                        tuple.getKeyNode() instanceof ScalarNode scalar ? scalar.getValue() : null;
                if (key != null
                        && !key.isEmpty()
                        && !key.chars().anyMatch(Character::isWhitespace)) {
                    found.addAll(
                            glossary.violations(
                                    path,
                                    tuple.getKeyNode().getStartMark().getLine() + 1,
                                    "key",
                                    key,
                                    Kind.IDENTIFIER));
                }
                walkYaml(
                        tuple.getValueNode(), path, key != null && SHELL_KEYS.contains(key), found);
            }
        } else if (node instanceof SequenceNode sequence) {
            sequence.getValue().forEach(item -> walkYaml(item, path, shell, found));
        } else if (node instanceof ScalarNode scalar) {
            String value = scalar.getValue();
            int line = scalar.getStartMark().getLine() + 1;
            if (value.isEmpty()) {
                return;
            }
            if (shell) {
                shellText(path, line, value, found);
            } else if (!glossary.isTextForPeople(value)) {
                found.addAll(glossary.violations(path, line, "value", value, Kind.LITERAL));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------------------------------

    List<Violation> json(String path, String content) {
        List<Violation> found = new ArrayList<>();
        int line = 1;
        int length = content.length();
        for (int i = 0; i < length; i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                line++;
            } else if (c == '"') {
                StringBuilder text = new StringBuilder();
                int j = i + 1;
                int startLine = line;
                while (j < length && content.charAt(j) != '"') {
                    if (content.charAt(j) == '\\' && j + 1 < length) {
                        text.append(content.charAt(j + 1));
                        j += 2;
                        continue;
                    }
                    if (content.charAt(j) == '\n') {
                        line++;
                    }
                    text.append(content.charAt(j++));
                }
                int next = j + 1;
                while (next < length && Character.isWhitespace(content.charAt(next))) {
                    next++;
                }
                boolean isKey = next < length && content.charAt(next) == ':';
                String value = text.toString();
                if (isKey) {
                    boolean prose =
                            value.chars().anyMatch(Character::isWhitespace)
                                    || value.chars().anyMatch(ch -> ch > 127);
                    if (!prose) {
                        found.addAll(
                                glossary.violations(
                                        path, startLine, "key", value, Kind.IDENTIFIER));
                    }
                } else if (!value.isEmpty() && !glossary.isTextForPeople(value)) {
                    found.addAll(
                            glossary.violations(path, startLine, "value", value, Kind.LITERAL));
                }
                i = j;
            }
        }
        return found;
    }

    /** Os valores de "scripts" do package.json são comandos: examinados palavra a palavra. */
    private List<Violation> packageScripts(String path, String content) {
        List<Violation> found = new ArrayList<>();
        Matcher block =
                Pattern.compile("\"scripts\"\\s*:\\s*\\{(.*?)}", Pattern.DOTALL).matcher(content);
        if (block.find()) {
            int firstLine =
                    1
                            + (int)
                                    content.substring(0, block.start(1))
                                            .chars()
                                            .filter(ch -> ch == '\n')
                                            .count();
            Matcher entry =
                    Pattern.compile("\"[^\"]+\"\\s*:\\s*\"([^\"]*)\"").matcher(block.group(1));
            while (entry.find()) {
                int line =
                        firstLine
                                + (int)
                                        block.group(1)
                                                .substring(0, entry.start())
                                                .chars()
                                                .filter(ch -> ch == '\n')
                                                .count();
                shellText(path, line, entry.group(1), found);
            }
        }
        return found;
    }

    // ---------------------------------------------------------------------------------------------
    // .properties, .env e scripts
    // ---------------------------------------------------------------------------------------------

    List<Violation> properties(String path, String content) {
        List<Violation> found = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        boolean continuation = false;
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            boolean wasContinuation = continuation;
            continuation = raw.endsWith("\\");
            String trimmed = raw.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            String valuePart = raw;
            if (!wasContinuation) {
                int separator = firstSeparator(raw);
                if (separator < 0) {
                    continue;
                }
                found.addAll(
                        glossary.violations(
                                path,
                                index + 1,
                                "key",
                                raw.substring(0, separator).strip(),
                                Kind.IDENTIFIER));
                valuePart = raw.substring(separator + 1);
            }
            Matcher marker = PLACEHOLDER.matcher(valuePart);
            while (marker.find()) {
                found.addAll(
                        glossary.violations(
                                path, index + 1, "placeholder", marker.group(1), Kind.IDENTIFIER));
            }
        }
        return found;
    }

    private static int firstSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '=' || c == ':') {
                return i;
            }
        }
        return -1;
    }

    List<Violation> env(String path, String content) {
        List<Violation> found = new ArrayList<>();
        Pattern variable = Pattern.compile("^#?\\s*([A-Z][A-Z0-9_]*)=(.*)$");
        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            Matcher matcher = variable.matcher(lines[index]);
            if (!matcher.matches()) {
                continue;
            }
            found.addAll(
                    glossary.violations(
                            path, index + 1, "variable", matcher.group(1), Kind.IDENTIFIER));
            String value = matcher.group(2).strip();
            if (!value.isEmpty() && !glossary.isTextForPeople(value)) {
                found.addAll(glossary.violations(path, index + 1, "value", value, Kind.LITERAL));
            }
        }
        return found;
    }

    List<Violation> shellLike(String path, String content) {
        List<Violation> found = new ArrayList<>();
        shellText(path, 1, content, found);
        return found;
    }

    /** Texto de shell: ignora comentários; aspas com texto para pessoas saem; o resto é código. */
    private void shellText(String path, int firstLine, String text, List<Violation> found) {
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            StringBuilder code = new StringBuilder();
            int i = 0;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                    break;
                }
                if ((c == '"' || c == '\'') && line.indexOf(c, i + 1) > i) {
                    int end = line.indexOf(c, i + 1);
                    String inner = line.substring(i + 1, end);
                    if (!inner.isEmpty() && !glossary.isTextForPeople(inner)) {
                        found.addAll(
                                glossary.violations(
                                        path, firstLine + index, "literal", inner, Kind.LITERAL));
                    }
                    code.append(' ');
                    i = end + 1;
                    continue;
                }
                code.append(c);
                i++;
            }
            if (!code.isEmpty()) {
                found.addAll(
                        glossary.violations(
                                path,
                                firstLine + index,
                                "shell",
                                code.toString(),
                                Kind.IDENTIFIER));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // XML e HTML
    // ---------------------------------------------------------------------------------------------

    List<Violation> markup(String path, String content, boolean textIsCode) {
        List<Violation> found = new ArrayList<>();
        String clean = blank(content, XML_COMMENT);
        Matcher tag = TAG.matcher(clean);
        while (tag.find()) {
            int line = lineOf(clean, tag.start());
            found.addAll(glossary.violations(path, line, "tag", tag.group(2), Kind.IDENTIFIER));
            Matcher attribute = ATTRIBUTE.matcher(tag.group(3));
            while (attribute.find()) {
                String attributeName = attribute.group(1);
                found.addAll(
                        glossary.violations(
                                path, line, "attribute", attributeName, Kind.IDENTIFIER));
                String value = attribute.group(2) != null ? attribute.group(2) : attribute.group(3);
                if (value == null) {
                    continue;
                }
                if (attributeName.endsWith("class") || attributeName.equals("id")) {
                    for (String part : value.split("\\s+")) {
                        found.addAll(
                                glossary.violations(path, line, "class", part, Kind.IDENTIFIER));
                    }
                } else if (!glossary.isTextForPeople(value)) {
                    found.addAll(glossary.violations(path, line, "value", value, Kind.LITERAL));
                }
                Matcher expression = EXPRESSION.matcher(value);
                while (expression.find()) {
                    shellText(path, line, expression.group(1), found);
                }
            }
        }
        Matcher text = TEXT_NODE.matcher(clean);
        while (text.find()) {
            String value = text.group(1).strip();
            int line = lineOf(clean, text.start(1));
            if (value.isEmpty()) {
                continue;
            }
            Matcher expression = EXPRESSION.matcher(value);
            while (expression.find()) {
                shellText(path, line, expression.group(1), found);
            }
            if (textIsCode && !glossary.isTextForPeople(value)) {
                found.addAll(glossary.violations(path, line, "value", value, Kind.LITERAL));
            }
        }
        Matcher style = STYLE_BLOCK.matcher(clean);
        while (style.find()) {
            found.addAll(css(path, style.group(1), lineOf(clean, style.start(1))));
        }
        return found;
    }

    // ---------------------------------------------------------------------------------------------
    // CSS e SQL
    // ---------------------------------------------------------------------------------------------

    List<Violation> css(String path, String content, int firstLine) {
        List<Violation> found = new ArrayList<>();
        String clean = blank(content, CSS_COMMENT);
        StringBuilder header = new StringBuilder();
        int depth = 0;
        int line = firstLine;
        int headerLine = firstLine;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '\n') {
                line++;
            }
            if (c == '{') {
                String selector = header.toString().strip();
                if (!selector.isEmpty()) {
                    found.addAll(
                            glossary.violations(
                                    path, headerLine, "selector", selector, Kind.IDENTIFIER));
                }
                header.setLength(0);
                depth++;
            } else if (c == '}') {
                depth--;
                header.setLength(0);
            } else if (c == ';' && depth > 0) {
                declaration(path, headerLine, header.toString(), found);
                header.setLength(0);
            } else {
                if (header.toString().isBlank()) {
                    headerLine = line;
                }
                header.append(c);
            }
        }
        if (!header.toString().isBlank() && depth > 0) {
            declaration(path, headerLine, header.toString(), found);
        }
        return found;
    }

    private void declaration(String path, int line, String text, List<Violation> found) {
        int colon = text.indexOf(':');
        if (colon < 0) {
            return;
        }
        String property = text.substring(0, colon).strip();
        String value = text.substring(colon + 1);
        found.addAll(glossary.violations(path, line, "property", property, Kind.IDENTIFIER));
        Matcher code = CSS_CODE.matcher(text);
        while (code.find()) {
            String reference =
                    code.group(1) != null
                            ? code.group(1)
                            : code.group(2) != null ? code.group(2) : code.group(3);
            if (reference != null && !reference.equals(property)) {
                found.addAll(
                        glossary.violations(
                                path, line, "css-reference", reference, Kind.IDENTIFIER));
            }
        }
        if (property.equals("animation") || property.equals("animation-name")) {
            for (String part : value.strip().split("\\s+")) {
                if (!part.isEmpty() && !Character.isDigit(part.charAt(0))) {
                    found.addAll(
                            glossary.violations(path, line, "animation", part, Kind.IDENTIFIER));
                }
            }
        }
    }

    List<Violation> sql(String path, String content) {
        List<Violation> found = new ArrayList<>();
        String clean = blank(blank(content, Pattern.compile("--[^\\n]*")), CSS_COMMENT);
        Matcher token = SQL_TOKEN.matcher(clean);
        while (token.find()) {
            int line = lineOf(clean, token.start());
            if (token.group(1) != null) {
                String literal = token.group(1);
                if (!literal.isEmpty() && !glossary.isTextForPeople(literal)) {
                    found.addAll(glossary.violations(path, line, "literal", literal, Kind.LITERAL));
                }
            } else {
                String identifier = token.group(2) != null ? token.group(2) : token.group();
                found.addAll(
                        glossary.violations(path, line, "identifier", identifier, Kind.IDENTIFIER));
            }
        }
        return found;
    }

    // ---------------------------------------------------------------------------------------------
    // Utilitários
    // ---------------------------------------------------------------------------------------------

    /**
     * Troca o que casa por espaços, preservando as quebras de linha (os números de linha continuam
     * certos).
     */
    private static String blank(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(content, last, matcher.start());
            for (int i = matcher.start(); i < matcher.end(); i++) {
                out.append(content.charAt(i) == '\n' ? '\n' : ' ');
            }
            last = matcher.end();
        }
        return out.append(content.substring(last)).toString();
    }

    static int lineOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
