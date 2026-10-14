package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.config.ApplicationProperties.ProactiveDelivery;
import br.com.felipeblaft.reports.config.ApplicationProperties.TelegramChannel;
import org.junit.jupiter.api.Test;

/** R-10, R-09, Princípio IV: configuração inconsistente derruba a subida, sem vazar segredo. */
class TelegramChannelConfigTest {

    private static TelegramChannel channel(ProactiveDelivery delivery, String url, int lease) {
        return new TelegramChannel(
                delivery,
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
        assertThatCode(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.DIRECT, "", 60), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void orchestratorWithoutUrlDoesNotStart() {
        assertThatThrownBy(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.ORCHESTRATOR, "", 60), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_CHANNEL_ORCHESTRATOR_URL");
        assertThatThrownBy(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.ORCHESTRATOR, "  ", 60), 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(
                                                ProactiveDelivery.ORCHESTRATOR,
                                                "http://n8n:5678/webhook/abc",
                                                60),
                                        1))
                .doesNotThrowAnyException();
    }

    @Test
    void directModeAcceptsEmptyUrl() {
        assertThat(channel(ProactiveDelivery.DIRECT, null, 60).configuredOrchestrator()).isFalse();
        assertThatCode(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.DIRECT, null, 60), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void leaseMustExceedConnectionPlusReply() {
        // 3000 + 10000 ms = 13 s
        assertThatThrownBy(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.DIRECT, "", 13), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease-seconds");
        assertThatThrownBy(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.DIRECT, "", 5), 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.DIRECT, "", 14), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void exactlyOneProactivePathMustBeActive() {
        assertThatThrownBy(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.DIRECT, "", 60), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exatamente um");
        assertThatThrownBy(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.DIRECT, "", 60), 2))
                .isInstanceOf(IllegalStateException.class);
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
        assertThatThrownBy(
                        () ->
                                TelegramChannelConfig.validate(
                                        channel(ProactiveDelivery.ORCHESTRATOR, url, 60), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https")
                .satisfies(
                        error ->
                                assertThat(error.getMessage())
                                        .doesNotContain("secret-in-path-123", "n8n.example"));
    }

    @Test
    void toStringMasksTokensAndUrl() {
        String text = channel(ProactiveDelivery.ORCHESTRATOR, "https://x/secret", 60).toString();
        assertThat(text)
                .doesNotContain("current", "next", "orchestrator-token", "secret")
                .contains("ORCHESTRATOR");
    }

    @Test
    void nullProactiveDeliveryBecomesDirect() {
        assertThat(channel(null, "", 60).proactiveDelivery()).isEqualTo(ProactiveDelivery.DIRECT);
    }
}
