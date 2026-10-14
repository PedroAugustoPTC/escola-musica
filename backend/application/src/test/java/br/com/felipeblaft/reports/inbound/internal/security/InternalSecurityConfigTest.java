package br.com.felipeblaft.reports.inbound.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.ApplicationProperties.ProactiveDelivery;
import br.com.felipeblaft.reports.config.ApplicationProperties.TelegramChannel;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** FR-026: sem nenhum token a subida avisa (sem imprimir valor algum); com token, não avisa. */
class InternalSecurityConfigTest {

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void capture() {
        logger = (Logger) LoggerFactory.getLogger(InternalSecurityConfig.class);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void release() {
        logger.detachAppender(events);
        events.stop();
    }

    private static TelegramChannel channel(String current, String next) {
        return new TelegramChannel(
                ProactiveDelivery.DIRECT,
                current,
                next,
                "",
                "",
                3000,
                10000,
                30,
                900,
                24,
                60,
                60,
                2000,
                20,
                24,
                7);
    }

    @Test
    void withoutAnyTokenEmitsOneNoticeWithoutValues() {
        InternalSecurityConfig.warnIfThereIsNoToken(channel("", null));
        InternalSecurityConfig.warnIfThereIsNoToken(channel("  ", ""));

        assertThat(events.list).hasSize(2);
        assertThat(events.list)
                .allSatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.WARN);
                            assertThat(event.getFormattedMessage())
                                    .contains("telegram-channel-without-service-token")
                                    .contains("TELEGRAM_CHANNEL_CURRENT_TOKEN");
                        });
    }

    @Test
    void withSomeTokenDoesNotWarnAndNeverPrintsToken() {
        InternalSecurityConfig.warnIfThereIsNoToken(channel("tok-secret-1", null));
        InternalSecurityConfig.warnIfThereIsNoToken(channel("", "tok-secret-2"));
        assertThat(events.list).isEmpty();
    }
}
