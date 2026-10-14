package br.com.felipeblaft.reports.architecture;

import br.com.felipeblaft.reports.architecture.GlossaryFile.Kind;
import br.com.felipeblaft.reports.architecture.GlossaryFile.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Léxico mínimo de código-fonte Java para o guardião (R-05): percorre o texto descartando
 * comentários, {@code package} e {@code import} (cada nome importado é declarado, e examinado, no
 * arquivo de origem), examina identificadores e literais de uma palavra e trata como código
 * embutido o SQL/JPQL de {@code @Query} e o JSON ou SQL de blocos de texto. Literais com espaço,
 * acento ou iniciados em maiúscula isolada são texto para pessoas (FR-023) e ficam de fora.
 */
public final class JavaSourceScanner {

    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern SQL_START =
            Pattern.compile("(?is)^\\s*(select|insert|update|delete|with|create|alter|drop)\\b.*");
    private static final Pattern CODE_WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final GlossaryFile glossary;

    public JavaSourceScanner(GlossaryFile glossary) {
        this.glossary = glossary;
    }

    /** Examina um arquivo .java e devolve as violações (path = caminho relativo do arquivo). */
    public List<Violation> scan(String path, String source) {
        List<Violation> found = new ArrayList<>();
        int length = source.length();
        int line = 1;
        int i = 0;
        String annotation = null; // última anotação lida
        int annotationDepth = 0; // profundidade de parênteses da anotação em curso (0 = fora)
        String activeAnnotation = null;
        boolean lineHasCode = false;

        while (i < length) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                lineHasCode = false;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // comentários
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
                while (i < length && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
                line += count(source, i, end);
                i = end;
                continue;
            }
            // bloco de texto
            if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int bodyEnd = end < 0 ? length : end;
                String body = source.substring(i + 3, bodyEnd);
                examineTextBlock(path, line, body, found);
                line += count(source, i, bodyEnd);
                i = end < 0 ? length : end + 3;
                lineHasCode = true;
                continue;
            }
            // literal de texto
            if (c == '"') {
                StringBuilder literal = new StringBuilder();
                int j = i + 1;
                while (j < length && source.charAt(j) != '"' && source.charAt(j) != '\n') {
                    if (source.charAt(j) == '\\' && j + 1 < length) {
                        literal.append(source.charAt(j + 1));
                        j += 2;
                        continue;
                    }
                    literal.append(source.charAt(j++));
                }
                examineLiteral(path, line, literal.toString(), activeAnnotation, found);
                i = Math.min(j + 1, length);
                lineHasCode = true;
                continue;
            }
            // literal de caractere
            if (c == '\'') {
                int j = i + 1;
                while (j < length && source.charAt(j) != '\'' && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                i = Math.min(j + 1, length);
                lineHasCode = true;
                continue;
            }
            if (c == '(' && annotation != null && annotationDepth == 0) {
                annotationDepth = 1;
                activeAnnotation = annotation;
                i++;
                continue;
            }
            if (c == '(' && annotationDepth > 0) {
                annotationDepth++;
            }
            if (c == ')' && annotationDepth > 0) {
                annotationDepth--;
                if (annotationDepth == 0) {
                    activeAnnotation = null;
                    annotation = null;
                }
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < length && Character.isJavaIdentifierPart(source.charAt(j))) {
                    j++;
                }
                String token = source.substring(i, j);
                boolean afterAt = i > 0 && source.charAt(i - 1) == '@';
                if (!lineHasCode && (token.equals("package") || token.equals("import"))) {
                    int end = source.indexOf(';', j);
                    end = end < 0 ? length : end + 1;
                    line += count(source, i, end);
                    i = end;
                    continue;
                }
                if (afterAt) {
                    annotation = token;
                }
                found.addAll(glossary.violations(path, line, "identifier", token, Kind.IDENTIFIER));
                lineHasCode = true;
                i = j;
                continue;
            }
            lineHasCode = true;
            i++;
        }
        return found;
    }

    private void examineLiteral(
            String path, int line, String literal, String annotation, List<Violation> found) {
        if (literal.isEmpty()) {
            return;
        }
        if ("Query".equals(annotation)) {
            examineCode(path, line, literal, "embedded-code", found);
            return;
        }
        if (glossary.isTextForPeople(literal)) {
            return;
        }
        found.addAll(glossary.violations(path, line, "literal", literal, Kind.LITERAL));
    }

    /** JSON e SQL dentro de blocos de texto são código; o resto é texto para pessoas. */
    private void examineTextBlock(String path, int line, String body, List<Violation> found) {
        String trimmed = body.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            examineJson(path, line, body, found);
        } else if (SQL_START.matcher(trimmed).matches()) {
            examineCode(path, line, body, "embedded-code", found);
        } else {
            // bloco de texto comum: texto para pessoas (a chamada registra o uso da exceção
            // embutida)
            glossary.isTextForPeople(trimmed);
        }
    }

    /**
     * Chaves e valores de uma palavra de um JSON em texto; valores com espaço ou acento são texto.
     */
    private void examineJson(String path, int firstLine, String json, List<Violation> found) {
        Matcher quoted = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (quoted.find()) {
            String value = quoted.group(1);
            int line = firstLine + count(json, 0, quoted.start());
            if (value.isEmpty() || glossary.isTextForPeople(value)) {
                continue;
            }
            found.addAll(glossary.violations(path, line, "embedded-code", value, Kind.LITERAL));
        }
    }

    /**
     * SQL/JPQL: cada identificador conta; literais de texto entre aspas simples seguem a regra do
     * literal.
     */
    private void examineCode(
            String path, int firstLine, String code, String kind, List<Violation> found) {
        Matcher matcher = Pattern.compile("'((?:[^']|'')*)'|" + CODE_WORD.pattern()).matcher(code);
        while (matcher.find()) {
            int line = firstLine + count(code, 0, matcher.start());
            if (matcher.group(1) != null) {
                String value = matcher.group(1);
                if (!value.isEmpty() && !glossary.isTextForPeople(value)) {
                    found.addAll(glossary.violations(path, line, kind, value, Kind.LITERAL));
                }
            } else {
                found.addAll(
                        glossary.violations(path, line, kind, matcher.group(), Kind.IDENTIFIER));
            }
        }
    }

    private static int count(String text, int from, int to) {
        int lines = 0;
        for (int k = from; k < to && k < text.length(); k++) {
            if (text.charAt(k) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
