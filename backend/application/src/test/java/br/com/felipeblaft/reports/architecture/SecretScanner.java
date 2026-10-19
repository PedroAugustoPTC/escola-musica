package br.com.felipeblaft.reports.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procura segredo, identificador de credencial e dado pessoal em arquivos versionados do n8n
 * (feature 004, FR-009, SC-005). A mensagem cita o arquivo, a linha e o código da regra, nunca o
 * valor.
 */
final class SecretScanner {

    private record Rule(String code, Pattern pattern, String reason) {}

    private static final Pattern IMAGE_DIGEST = Pattern.compile("sha256:[0-9a-fA-F]{64}");

    private static final List<Rule> RULES =
            List.of(
                    new Rule(
                            "CREDENTIAL_KEY",
                            Pattern.compile("\"(credentials|credentialId)\"\\s*:"),
                            "chave de credencial do n8n"),
                    new Rule(
                            "BOT_TOKEN",
                            Pattern.compile("(?<![0-9])[0-9]{6,}:[A-Za-z0-9_-]{30,}"),
                            "token de bot"),
                    new Rule(
                            "API_KEY",
                            Pattern.compile("\\bgsk_[A-Za-z0-9]{10,}|\\bsk-[A-Za-z0-9_-]{10,}"),
                            "chave de API de provedor"),
                    new Rule(
                            "BEARER",
                            Pattern.compile("Bearer\\s+[A-Za-z0-9._~+/=-]{8,}"),
                            "cabeçalho de autorização literal"),
                    new Rule(
                            "LONG_HEX",
                            Pattern.compile("(?<![0-9a-fA-F])[0-9a-fA-F]{32,}(?![0-9a-fA-F])"),
                            "sequência hexadecimal longa"),
                    new Rule(
                            "LONG_BASE64",
                            Pattern.compile("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{32,}={0,2}"),
                            "sequência base64 longa"),
                    new Rule(
                            "URL_USERINFO",
                            Pattern.compile("://[^/\\s:@\"']+:[^/\\s@\"']+@"),
                            "URL com usuário e senha"),
                    new Rule(
                            "URL_PATH_SECRET",
                            Pattern.compile(
                                    "https?://[^\\s\"']+/[A-Za-z0-9_-]{24,}(?=[/?\\s\"']|$)"),
                            "segredo no caminho da URL"),
                    new Rule(
                            "CPF",
                            Pattern.compile(
                                    "(?<![0-9])[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}(?![0-9])|(?<![0-9])[0-9]{11}(?![0-9])"),
                            "número de CPF"));

    private SecretScanner() {}

    /** Violações de um arquivo; cada uma começa por {@code [CÓDIGO]} e traz a linha. */
    static List<String> scan(String path, String content) {
        List<String> found = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = IMAGE_DIGEST.matcher(lines[i]).replaceAll("sha256:DIGEST");
            for (Rule rule : RULES) {
                Matcher matcher = rule.pattern().matcher(line);
                boolean hit = false;
                while (matcher.find()) {
                    if (rule.code().equals("LONG_BASE64") && !mixed(matcher.group())) {
                        continue;
                    }
                    hit = true;
                    break;
                }
                if (hit) {
                    found.add(
                            "["
                                    + rule.code()
                                    + "] "
                                    + path
                                    + " linha "
                                    + (i + 1)
                                    + ": "
                                    + rule.reason());
                }
            }
        }
        return found;
    }

    /** Base64 de segredo mistura maiúscula, minúscula e dígito; caminho e nome comum não. */
    private static boolean mixed(String text) {
        return text.chars().anyMatch(Character::isUpperCase)
                && text.chars().anyMatch(Character::isLowerCase)
                && text.chars().anyMatch(Character::isDigit);
    }
}
