package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Autoteste da varredura de segredos. Os arquivos em {@code
 * src/test/resources/workflow-guard/secret-scan/} só trazem marcadores ({@code @BOT_TOKEN@}…): os
 * valores falsos são gerados aqui, em tempo de execução, para que nenhum arquivo versionado pareça
 * um segredo.
 */
class WorkflowSecretScanSelfTest {

    private static final Map<String, String> FAKES =
            Map.ofEntries(
                    Map.entry("@BOT_TOKEN@", "123456789" + ":" + "Ab".repeat(20)),
                    Map.entry("@GSK@", "gsk" + "_" + "Qw3rTy".repeat(8)),
                    Map.entry("@SK@", "sk" + "-" + "Zx9yWv".repeat(6)),
                    Map.entry("@BEARER@", "Tk" + "n".repeat(12) + "9"),
                    Map.entry("@HEX@", "a1b2c3d4".repeat(8)),
                    Map.entry("@B64@", "Ab9".repeat(15) + "xyz"),
                    Map.entry("@USERINFO@", "operator:" + "pw1234xyz"),
                    Map.entry("@PATH_SECRET@", "k9" + "Ab".repeat(13)),
                    Map.entry("@CPF@", "123.456.789-09"),
                    Map.entry("@DIGEST@", "0f".repeat(32)));

    private static Path fixtures() {
        return Path.of("src/test/resources/workflow-guard/secret-scan").toAbsolutePath();
    }

    private static String expand(String fixture) throws IOException {
        String text = Files.readString(fixtures().resolve(fixture));
        for (Map.Entry<String, String> fake : FAKES.entrySet()) {
            text = text.replace(fake.getKey(), fake.getValue());
        }
        return text;
    }

    static Stream<Arguments> positives() {
        return Stream.of(
                Arguments.of("credential-key.json", "CREDENTIAL_KEY"),
                Arguments.of("credential-id.json", "CREDENTIAL_KEY"),
                Arguments.of("bot-token.txt", "BOT_TOKEN"),
                Arguments.of("groq-key.txt", "API_KEY"),
                Arguments.of("openai-key.txt", "API_KEY"),
                Arguments.of("bearer.txt", "BEARER"),
                Arguments.of("long-hex.txt", "LONG_HEX"),
                Arguments.of("long-base64.txt", "LONG_BASE64"),
                Arguments.of("url-userinfo.txt", "URL_USERINFO"),
                Arguments.of("url-path-secret.txt", "URL_PATH_SECRET"),
                Arguments.of("cpf.txt", "CPF"));
    }

    @ParameterizedTest(name = "{0} reprova com {1}")
    @MethodSource("positives")
    @DisplayName("cada tipo de segredo é detectado")
    void eachKindIsDetected(String fixture, String code) throws IOException {
        List<String> violations = SecretScanner.scan(fixture, expand(fixture));

        assertThat(violations).anyMatch(v -> v.startsWith("[" + code + "]"));
    }

    @Test
    @DisplayName("digest da imagem, URLs comuns e caminhos não são segredo")
    void commonContentIsNotASecret() throws IOException {
        assertThat(SecretScanner.scan("clean.txt", expand("clean.txt"))).isEmpty();
    }

    @Test
    @DisplayName("a mensagem nunca traz o valor encontrado")
    void messageNeverRevealsTheValue() throws IOException {
        String content = expand("bot-token.txt");

        assertThat(SecretScanner.scan("bot-token.txt", content))
                .isNotEmpty()
                .allSatisfy(v -> assertThat(v).doesNotContain(FAKES.get("@BOT_TOKEN@")));
    }
}
