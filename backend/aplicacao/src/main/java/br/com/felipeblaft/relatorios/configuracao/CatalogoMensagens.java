package br.com.felipeblaft.relatorios.configuracao;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
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
public class CatalogoMensagens {

    public static final String RECURSO = "mensagens/mensagens_pt_BR.properties";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    private final Properties textos = new Properties();

    public CatalogoMensagens() {
        try (InputStream entrada =
                Objects.requireNonNull(
                        CatalogoMensagens.class.getClassLoader().getResourceAsStream(RECURSO),
                        "catálogo de mensagens ausente")) {
            textos.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    public boolean contem(String chave) {
        return textos.containsKey(chave);
    }

    public Set<String> chaves() {
        return textos.stringPropertyNames();
    }

    /** Texto com os placeholders ainda não substituídos. */
    public String modelo(String chave) {
        return textos.getProperty(chave);
    }

    public String texto(String chave) {
        return texto(chave, Map.of());
    }

    public String texto(String chave, Map<String, ?> parametros) {
        String modelo = textos.getProperty(chave);
        if (modelo == null) {
            throw new IllegalArgumentException("mensagem sem texto no catálogo: " + chave);
        }
        Matcher placeholder = PLACEHOLDER.matcher(modelo);
        StringBuilder resultado = new StringBuilder();
        while (placeholder.find()) {
            Object valor = parametros.get(placeholder.group(1));
            placeholder.appendReplacement(
                    resultado, Matcher.quoteReplacement(valor == null ? "" : valor.toString()));
        }
        placeholder.appendTail(resultado);
        return resultado.toString();
    }

    public String texto(CodigoErro codigo, Map<String, ?> parametros) {
        return texto(codigo.chaveMensagem(), parametros);
    }
}
