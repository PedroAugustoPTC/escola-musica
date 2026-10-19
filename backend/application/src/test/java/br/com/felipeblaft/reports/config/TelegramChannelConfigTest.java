package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.config.ApplicationProperties.TelegramChannel;
import org.junit.jupiter.api.Test;

/** R-09, Princípio IV: configuração inconsistente derruba a subida, sem vazar segredo. */
class TelegramChannelConfigTest {

    private static TelegramChannel channel(String url, int lease) {
        return new TelegramChannel(
                "current",
                "next",
                url,
                "orchestrator-token",
                3000,
                10000,
                30,
                900,
                24,
                60,
                lease,
                2000,
                20,
                24,
                7);
    }

    @Test
    void defaultConfigIsValid() {
        assertThatCode(() -> TelegramChannelConfig.validate(channel("", 60), false))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledTelegramWithoutOrchestratorUrlDoesNotStart() {
        assertThatThrownBy(() -> TelegramChannelConfig.validate(channel("", 60), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_CHANNEL_ORCHESTRATOR_URL");
        assertThatThrownBy(() -> TelegramChannelConfig.validate(channel("  ", 60), true))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel("http://n8n:5678/webhook/abc", 60), true))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledTelegramAcceptsEmptyUrl() {
        assertThat(channel(null, 60).configuredOrchestrator()).isFalse();
        assertThatCode(() -> TelegramChannelConfig.validate(channel(null, 60), false))
                .doesNotThrowAnyException();
    }

    @Test
    void leaseMustExceedConnectionPlusReply() {
        // 3000 + 10000 ms = 13 s
        assertThatThrownBy(() -> TelegramChannelConfig.validate(channel("", 13), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease-seconds");
        assertThatThrownBy(() -> TelegramChannelConfig.validate(channel("", 5), false))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> TelegramChannelConfig.validate(channel("", 14), false))
                .doesNotThrowAnyException();
    }

    @Test
    void publicUrlRequiresHttpsAndHttpOnlyOnInternalNetwork() {
        for (String allowed :
                new String[] {
                    "https://n8n.example.com.br/webhook/x",
                    "http://n8n:5678/webhook/x",
                    "http://localhost:5678/x",
                    "http://127.0.0.1:5678/x",
                    "http://10.0.3.4:5678/x",
                    "http://172.20.0.5/x",
                    "http://192.168.1.10:8080/x",
                    "http://[::1]:5678/x"
                }) {
            assertThat(TelegramChannelConfig.allowedDestination(allowed)).as(allowed).isTrue();
        }
        for (String refused :
                new String[] {
                    "http://n8n.example.com.br/webhook/x",
                    "http://8.8.8.8/x",
                    "http://203.0.113.9:5678/x",
                    "ftp://n8n/x",
                    "n8n:5678/x",
                    "http:///x",
                    "isto não é url"
                }) {
            assertThat(TelegramChannelConfig.allowedDestination(refused)).as(refused).isFalse();
        }
    }

    @Test
    void publicUrlWithoutHttpsDoesNotStartAndWithoutLeakingSecretPath() {
        String url = "http://n8n.example.com.br/webhook/secret-in-path-123";
        assertThatThrownBy(() -> TelegramChannelConfig.validate(channel(url, 60), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https")
                .satisfies(
                        error ->
                                assertThat(error.getMessage())
                                        .doesNotContain("secret-in-path-123", "n8n.example"));
    }

    @Test
    void toStringMasksTokensAndUrl() {
        String text = channel("https://x/secret", 60).toString();
        assertThat(text).doesNotContain("current", "next", "orchestrator-token", "secret");
    }
}
