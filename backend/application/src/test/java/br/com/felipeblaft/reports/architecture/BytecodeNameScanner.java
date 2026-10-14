package br.com.felipeblaft.reports.architecture;

import br.com.felipeblaft.reports.architecture.GlossaryFile.Kind;
import br.com.felipeblaft.reports.architecture.GlossaryFile.Violation;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Examina os nomes que sobrevivem no bytecode (ArchUnit): pacote, tipo (inclusive aninhado),
 * métodos, campos, constantes de enumeração, componentes de {@code record} e valores de uma palavra
 * de anotações ({@code @Table}, {@code @Column}, {@code @RequestMapping}, {@code @JsonProperty},
 * {@code @Value}…). Complementa a varredura de fonte, que cobre o que o bytecode não guarda
 * (variáveis locais, parâmetros).
 */
public final class BytecodeNameScanner {

    private final GlossaryFile glossary;

    public BytecodeNameScanner(GlossaryFile glossary) {
        this.glossary = glossary;
    }

    /** {@code pathOf} liga cada classe ao arquivo de fonte (o caminho usado nas exclusões). */
    public List<Violation> scan(JavaClasses classes, Function<JavaClass, String> pathOf) {
        List<Violation> found = new ArrayList<>();
        for (JavaClass type : classes) {
            String path = pathOf.apply(type);
            if (path.startsWith("classpath:")) {
                // sem arquivo de fonte: o pacote não tem pasta para ser examinada, então é
                // examinado aqui
                add(found, path, "package", type.getPackageName(), Kind.IDENTIFIER);
            }
            add(found, path, "class", type.getSimpleName(), Kind.IDENTIFIER);
            annotations(found, path, type.getAnnotations());
            for (JavaMethod method : type.getMethods()) {
                if (!synthetic(method.getName())) {
                    add(found, path, "method", method.getName(), Kind.IDENTIFIER);
                }
                annotations(found, path, method.getAnnotations());
            }
            for (JavaField field : type.getFields()) {
                if (!synthetic(field.getName())) {
                    add(found, path, "field", field.getName(), Kind.IDENTIFIER);
                }
                annotations(found, path, field.getAnnotations());
            }
        }
        return found;
    }

    private static boolean synthetic(String name) {
        return name.contains("$") || name.startsWith("<");
    }

    private void annotations(
            List<Violation> found, String path, Iterable<? extends JavaAnnotation<?>> annotations) {
        for (JavaAnnotation<?> annotation : annotations) {
            for (Map.Entry<String, Object> property : annotation.getProperties().entrySet()) {
                Object value = property.getValue();
                if (value instanceof String text) {
                    literal(found, path, text);
                } else if (value instanceof Object[] array) {
                    for (Object item : array) {
                        if (item instanceof String text) {
                            literal(found, path, text);
                        }
                    }
                }
            }
        }
    }

    private void literal(List<Violation> found, String path, String text) {
        if (!text.isEmpty() && !glossary.isTextForPeople(text)) {
            add(found, path, "annotation-value", text, Kind.LITERAL);
        }
    }

    private void add(List<Violation> found, String path, String kind, String text, Kind where) {
        if (!text.isEmpty()) {
            found.addAll(glossary.violations(path, 1, kind, text, where));
        }
    }
}
