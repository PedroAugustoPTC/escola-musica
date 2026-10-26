package br.com.felipeblaft.reports.config.log;

import java.util.List;
import java.util.regex.Pattern;
import net.logstash.logback.mask.ValueMasker;
import tools.jackson.core.TokenStreamContext;

/**
 * Defesa em profundidade para FR-055 e FR-039: mascara em qualquer valor de log o que parecer CPF,
 * telefone, e-mail, senha, código de acesso (8 caracteres) ou de ativação (12 caracteres) e token
 * de sessão, e esconde por completo os campos de conteúdo, credencial e contato (inclusive os do
 * responsável). Nome e data de nascimento não são mascaráveis: a garantia deles é logar só
 * identificadores e códigos. A regra principal continua sendo não registrar esses dados.
 */
public class SensitiveDataMaskingConverter implements ValueMasker {

    static final String MASK = "***";

    private static final List<String> HIDDEN_FIELDS =
            List.of(
                    "transcription",
                    "text",
                    "content",
                    "cpf",
                    "code",
                    "token",
                    "password",
                    "newPassword",
                    "currentPassword",
                    "senha",
                    "email",
                    "phone",
                    "activationCode",
                    "accessCode");

    /** Prefixo dos campos de contato do responsável (nome, telefone, e-mail). */
    private static final String HIDDEN_PREFIX = "guardian";

    /** Chave e valor de senha em texto livre ({@code password=...}, {@code "senha":"..."}). */
    private static final Pattern PASSWORD_PAIR =
            Pattern.compile(
                    "(?i)(\\b(?:password|newPassword|currentPassword|senha)\\b\"?\\s*[=:]\\s*)"
                            + "(\"[^\"]*\"|\\S+)");

    private static final List<Pattern> PATTERNS =
            List.of(
                    // CPF com ou sem pontuação
                    Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b"),
                    // Telefone brasileiro (DDD opcional entre parênteses, 8 ou 9 dígitos), sem
                    // tocar em pedaços de UUID nem em números curtos
                    Pattern.compile(
                            "(?<![\\w-])(?:\\+?55[\\s-]?)?(?:\\(\\d{2}\\)|\\d{2})[\\s-]?9?\\d{4}"
                                    + "[\\s-]?\\d{4}(?![\\w-])"),
                    // E-mail
                    Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
                    // Código de ativação do administrador: 12 caracteres do alfabeto de acesso
                    Pattern.compile("\\b[A-HJ-NP-Z2-9]{12}\\b"),
                    // Código de acesso: 8 caracteres do alfabeto, com ao menos um dígito
                    Pattern.compile("\\b(?=[A-HJ-NP-Z2-9]*[2-9])[A-HJ-NP-Z2-9]{8}\\b"),
                    // Tokens opacos (Base64 URL de 32 bytes) e cabeçalhos de autorização
                    Pattern.compile("\\b[A-Za-z0-9_-]{43}\\b"),
                    Pattern.compile("(?i)(bearer|bot)[0-9]*[: ]?[A-Za-z0-9:_-]{20,}"));

    @Override
    public Object mask(TokenStreamContext context, Object value) {
        String field = context.currentName();
        if (field != null && (HIDDEN_FIELDS.contains(field) || field.startsWith(HIDDEN_PREFIX))) {
            return MASK;
        }
        if (value instanceof CharSequence text) {
            return maskText(text.toString());
        }
        return null;
    }

    /** Aplica os padrões a um texto livre; devolve {@code null} se nada mudou. */
    static String maskText(String text) {
        String result = PASSWORD_PAIR.matcher(text).replaceAll("$1" + MASK);
        for (Pattern defaults : PATTERNS) {
            result = defaults.matcher(result).replaceAll(MASK);
        }
        return result.equals(text) ? null : result;
    }
}
