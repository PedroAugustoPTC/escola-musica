package br.com.felipeblaft.reports.config.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Defesa em profundidade do log (FR-039, FR-055): CPF, telefone, e-mail, senha e códigos de acesso
 * e de ativação nunca saem em mensagens nem em argumentos. Nome e data de nascimento não são
 * mascaráveis: a garantia deles é a disciplina de logar só identificadores e códigos.
 */
class SensitiveDataMaskingConverterTest {

    private static final String MASK = SensitiveDataMaskingConverter.MASK;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static String masked(String text) {
        String result = SensitiveDataMaskingConverter.maskText(text);
        return result == null ? text : result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "529982247-25"})
    void masksCpfWithOrWithoutPunctuation(String cpf) {
        assertThat(masked("aluno com CPF " + cpf + " recusado"))
                .isEqualTo("aluno com CPF " + MASK + " recusado")
                .doesNotContain("529");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "(11) 98765-4321",
                "11 98765-4321",
                "11987654321",
                "(11) 3333-4444",
                "1133334444",
                "+55 11 98765-4321"
            })
    void masksBrazilianPhones(String phone) {
        String result = masked("contato " + phone + " informado");
        assertThat(result).contains(MASK).doesNotContain("4321").doesNotContain("4444");
    }

    @Test
    void doesNotDamageUuidsOrShortNumbers() {
        String text = "id 0f8fad5b-d9cb-469f-a165-70867728950e update 12345 jobs 42";
        assertThat(masked(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"admin@school.example.com", "mary.smith+test@example.org", "a_b-c@x.io"})
    void masksEmails(String email) {
        assertThat(masked("conta " + email + " não encontrada"))
                .isEqualTo("conta " + MASK + " não encontrada");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "password=Hunter2-Passphrase-123",
                "password: Hunter2-Passphrase-123",
                "newPassword=Hunter2-Passphrase-123",
                "senha=Hunter2-Passphrase-123",
                "\"password\":\"Hunter2-Passphrase-123\""
            })
    void masksPasswordsInText(String text) {
        assertThat(masked("falha " + text + " fim")).doesNotContain("Passphrase");
    }

    @Test
    void masksAccessCodeAndActivationCode() {
        assertThat(masked("código K7QM2XPA recusado")).isEqualTo("código " + MASK + " recusado");
        assertThat(masked("ativação K7QM2XPA9WTE recusada"))
                .isEqualTo("ativação " + MASK + " recusada");
        assertThat(masked("ativação ABCDEFGHJKLM recusada")).doesNotContain("ABCDEFGHJKLM");
    }

    @Test
    void keepsErrorCodesAndOrdinaryWords() {
        String text = "recusa OPERATION_TEMPORARILY_BLOCKED por UNAUTHORIZED em ADMINISTRATOR";
        assertThat(masked(text)).isEqualTo(text);
    }

    @Test
    void masksOpaqueTokensAndAuthorizationHeaders() {
        assertThat(masked("token " + "A".repeat(43))).isEqualTo("token " + MASK);
        assertThat(masked("Authorization: Bearer abcdefghijklmnopqrstuvwxyz0123"))
                .doesNotContain("abcdefghijklmnop");
    }

    @Test
    void returnsNullWhenNothingChanged() {
        assertThat(SensitiveDataMaskingConverter.maskText("nada sensível aqui")).isNull();
    }

    @Test
    void hidesWholeFieldsByName() throws Exception {
        for (String field :
                new String[] {
                    "password",
                    "newPassword",
                    "currentPassword",
                    "senha",
                    "email",
                    "phone",
                    "cpf",
                    "code",
                    "token",
                    "activationCode",
                    "guardianName",
                    "guardianPhone",
                    "guardianEmail",
                    "transcription",
                    "text"
                }) {
            assertThat(valueOfField(field, "any-value-here")).as("campo %s", field).isEqualTo(MASK);
        }
    }

    @Test
    void keepsHarmlessFieldsAndMasksTheirTextValues() throws Exception {
        assertThat(valueOfField("operation", "TEACHER_REGISTER")).isNull();
        assertThat(valueOfField("detail", "contato 11987654321")).isEqualTo("contato " + MASK);
        assertThat(valueOfField("count", 3)).isNull();
    }

    @Test
    void masksMessagesAndArgumentsOfARealLogEvent() {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        LogstashEncoder encoder = new LogstashEncoder();
        MaskingJsonGeneratorDecorator decorator = new MaskingJsonGeneratorDecorator();
        decorator.addValueMasker(new SensitiveDataMaskingConverter());
        decorator.start();
        encoder.addDecorator(decorator);
        encoder.setContext(context);
        encoder.start();
        LoggingEvent event =
                new LoggingEvent(
                        LoggerContext.class.getName(),
                        context.getLogger("test"),
                        Level.INFO,
                        "falha de {} com CPF {} e telefone {}",
                        null,
                        new Object[] {
                            "mary@school.example.com", "529.982.247-25", "(11) 98765-4321"
                        });

        String line = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(line)
                .contains(MASK)
                .doesNotContain("mary@")
                .doesNotContain("529.982")
                .doesNotContain("98765");
    }

    /** Valor devolvido pelo mascarador para o campo, no contexto real de um gerador JSON. */
    private static Object valueOfField(String field, Object value) throws Exception {
        try (JsonGenerator generator = JSON.createGenerator(new StringWriter())) {
            generator.writeStartObject();
            generator.writeName(field);
            return new SensitiveDataMaskingConverter().mask(generator.streamWriteContext(), value);
        }
    }
}
