package br.com.felipeblaft.reports.config.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Log do perfil {@code seed} (comandos operacionais): mensagem mascarada como nos demais perfis e
 * exceção só pelo tipo, porque a mensagem do Hibernate ou do PostgreSQL traz {@code Key
 * (email)=(...)} e outros valores.
 */
class SeedLogConvertersTest {

    private static final Logger LOGGER = new LoggerContext().getLogger("seed");

    private static LoggingEvent event(String message, Throwable error) {
        LoggingEvent event = new LoggingEvent("x", LOGGER, Level.ERROR, message, error, null);
        return event;
    }

    @Test
    void messageIsMaskedLikeTheJsonProfile() {
        String text =
                new MaskedMessageConverter()
                        .convert(event("falha para ana@example.test cpf 529.982.247-25", null));

        assertThat(text).doesNotContain("ana@example.test").doesNotContain("529.982");
        assertThat(text).contains(SensitiveDataMaskingConverter.MASK);
    }

    @Test
    void cleanMessageIsUntouched() {
        assertThat(new MaskedMessageConverter().convert(event("Carga concluída.", null)))
                .isEqualTo("Carga concluída.");
    }

    @Test
    void exceptionShowsOnlyTheTypesNeverTheMessageOrTheStack() {
        Throwable root = new IllegalStateException("Key (email)=(ana@example.test) already exists");
        Throwable error = new RuntimeException("detalhe com 529.982.247-25", root);

        String text = new ExceptionTypeConverter().convert(event("falha", error));

        assertThat(text)
                .contains("RuntimeException")
                .contains("IllegalStateException")
                .doesNotContain("ana@example.test")
                .doesNotContain("529.982")
                .doesNotContain("Key (email)")
                .doesNotContain("\tat ")
                .doesNotContain("\n");
    }

    @Test
    void noExceptionMeansNoText() {
        assertThat(new ExceptionTypeConverter().convert(event("ok", null))).isEmpty();
    }

    @Test
    void exceptionWithoutCauseNamesItOnce() {
        String text =
                new ExceptionTypeConverter()
                        .convert(event("falha", new IllegalStateException("x")));

        assertThat(text).contains("IllegalStateException");
        assertThat(text.indexOf("IllegalStateException"))
                .isEqualTo(text.lastIndexOf("IllegalStateException"));
    }

    @Test
    void seedProfileUsesTheSafeConvertersAndSilencesTheHibernateErrorLogger() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        String seed =
                xml.substring(
                        xml.indexOf("<springProfile name=\"seed\">"),
                        xml.indexOf("<springProfile name=\"!seed\">"));

        assertThat(seed)
                .contains("MaskedMessageConverter")
                .contains("ExceptionTypeConverter")
                .contains("org.hibernate.orm.jdbc.error")
                .doesNotContainPattern("%(msg|message|ex|exception|throwable|rEx)\b");
    }
}
