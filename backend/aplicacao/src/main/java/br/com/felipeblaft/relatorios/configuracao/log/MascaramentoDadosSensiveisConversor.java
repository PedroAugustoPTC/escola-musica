package br.com.felipeblaft.relatorios.configuracao.log;

import java.util.List;
import java.util.regex.Pattern;
import net.logstash.logback.mask.ValueMasker;
import tools.jackson.core.TokenStreamContext;

/**
 * Defesa em profundidade para FR-055: mascara em qualquer valor de log o que parecer CPF, código de
 * acesso ou token de sessão, e esconde por completo os campos {@code transcricao} e {@code texto}.
 * A regra principal continua sendo não registrar esses dados.
 */
public class MascaramentoDadosSensiveisConversor implements ValueMasker {

    static final String MASCARA = "***";

    private static final List<String> CAMPOS_OCULTOS =
            List.of("transcricao", "texto", "conteudo", "cpf", "codigo", "token");

    private static final List<Pattern> PADROES =
            List.of(
                    // CPF com ou sem pontuação
                    Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b"),
                    // Código de acesso: 8 caracteres do alfabeto, com ao menos um dígito
                    Pattern.compile(
                            "\\b(?=[A-HJ-NP-Z2-9]*[2-9])[A-HJ-NP-Z2-9]{8}\\b"),
                    // Tokens opacos (Base64 URL de 32 bytes) e cabeçalhos de autorização
                    Pattern.compile("\\b[A-Za-z0-9_-]{43}\\b"),
                    Pattern.compile("(?i)(bearer|bot)[0-9]*[: ]?[A-Za-z0-9:_-]{20,}"));

    @Override
    public Object mask(TokenStreamContext contexto, Object valor) {
        String campo = contexto.currentName();
        if (campo != null && CAMPOS_OCULTOS.contains(campo)) {
            return MASCARA;
        }
        if (valor instanceof CharSequence texto) {
            return mascarar(texto.toString());
        }
        return null;
    }

    /** Aplica os padrões a um texto livre; devolve {@code null} se nada mudou. */
    static String mascarar(String texto) {
        String resultado = texto;
        for (Pattern padrao : PADROES) {
            resultado = padrao.matcher(resultado).replaceAll(MASCARA);
        }
        return resultado.equals(texto) ? null : resultado;
    }
}
