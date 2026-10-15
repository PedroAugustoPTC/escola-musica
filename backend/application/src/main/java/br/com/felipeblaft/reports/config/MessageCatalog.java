package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Catálogo único de mensagens (FR-003, R-18): os dois canais buscam aqui o texto de cada código.
 * Placeholders {@code {nome}} são substituídos pelos parâmetros; os ausentes ficam vazios.
 */
@Component
public class MessageCatalog {

    public static final String RESOURCE = "messages/messages_pt_BR.properties";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    private final Properties texts = new Properties();

    public MessageCatalog() {
        try (InputStream input =
                Objects.requireNonNull(
                        MessageCatalog.class.getClassLoader().getResourceAsStream(RESOURCE),
                        "catálogo de mensagens ausente")) {
            texts.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public boolean contains(String key) {
        return texts.containsKey(key);
    }

    public Set<String> keys() {
        return texts.stringPropertyNames();
    }

    /** Texto com os placeholders ainda não substituídos. */
    public String model(String key) {
        return texts.getProperty(key);
    }

    public String text(String key) {
        return text(key, Map.of());
    }

    public String text(String key, Map<String, ?> parameters) {
        String model = texts.getProperty(key);
        if (model == null) {
            throw new IllegalArgumentException("mensagem sem texto no catálogo: " + key);
        }
        Matcher placeholder = PLACEHOLDER.matcher(model);
        StringBuilder result = new StringBuilder();
        while (placeholder.find()) {
            Object value = parameters.get(placeholder.group(1));
            placeholder.appendReplacement(
                    result, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        placeholder.appendTail(result);
        return result.toString();
    }

    public String text(ErrorCode code, Map<String, ?> parameters) {
        return text(code.messageKey(), parameters);
    }
}
