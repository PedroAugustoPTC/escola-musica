package br.com.felipeblaft.reports.config.log;

import java.util.List;
import java.util.regex.Pattern;
import net.logstash.logback.mask.ValueMasker;
import tools.jackson.core.TokenStreamContext;

/**
 * Defesa em profundidade para FR-055: mascara em qualquer valor de log o que parecer CPF, código de
 * acesso ou token de sessão, e esconde por completo os campos {@code transcricao} e {@code texto}.
 * A regra principal continua sendo não registrar esses dados.
 */
public class SensitiveDataMaskingConverter implements ValueMasker {

    static final String MASK = "***";

    private static final List<String> HIDDEN_FIELDS =
            List.of("transcription", "text", "content", "cpf", "code", "token");

    private static final List<Pattern> PATTERNS =
            List.of(
                    // CPF com ou sem pontuação
                    Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b"),
                    // Código de acesso: 8 caracteres do alfabeto, com ao menos um dígito
                    Pattern.compile("\\b(?=[A-HJ-NP-Z2-9]*[2-9])[A-HJ-NP-Z2-9]{8}\\b"),
                    // Tokens opacos (Base64 URL de 32 bytes) e cabeçalhos de autorização
                    Pattern.compile("\\b[A-Za-z0-9_-]{43}\\b"),
                    Pattern.compile("(?i)(bearer|bot)[0-9]*[: ]?[A-Za-z0-9:_-]{20,}"));

    @Override
    public Object mask(TokenStreamContext context, Object value) {
        String field = context.currentName();
        if (field != null && HIDDEN_FIELDS.contains(field)) {
            return MASK;
        }
        if (value instanceof CharSequence text) {
            return maskText(text.toString());
        }
        return null;
    }

    /** Aplica os padrões a um texto livre; devolve {@code null} se nada mudou. */
    static String maskText(String text) {
        String result = text;
        for (Pattern defaults : PATTERNS) {
            result = defaults.matcher(result).replaceAll(MASK);
        }
        return result.equals(text) ? null : result;
    }
}
