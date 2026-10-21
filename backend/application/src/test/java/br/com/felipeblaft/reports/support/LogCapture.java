package br.com.felipeblaft.reports.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 * Captura os eventos do logback durante um teste (SC-004): a aplicação em DEBUG e o restante em
 * INFO, que é o pior caso para vazamento. {@link #text()} junta mensagem formatada, MDC, pares
 * chave-valor e o rastro completo das exceções (com as causas), o texto que o varredor examina. Use
 * em {@code try}-com-recursos: ao fechar, o apêndice sai e os níveis voltam ao que eram.
 */
public final class LogCapture implements AutoCloseable {

    private static final String APPLICATION_LOGGER = "br.com.felipeblaft";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root;
    private final Logger application;
    private final Level rootLevel;
    private final Level applicationLevel;
    private boolean closed;

    private LogCapture() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        application = (Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        rootLevel = root.getLevel();
        applicationLevel = application.getLevel();
    }

    /** Começa a capturar: raiz em INFO, aplicação em DEBUG. */
    public static LogCapture start() {
        LogCapture capture = new LogCapture();
        capture.root.setLevel(Level.INFO);
        capture.application.setLevel(Level.DEBUG);
        capture.appender.start();
        capture.root.addAppender(capture.appender);
        return capture;
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /** Só as mensagens formatadas, uma por evento. */
    public List<String> messages() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Tudo o que um evento pode carregar, para a varredura. */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : events()) {
            text.append(event.getLevel())
                    .append(' ')
                    .append(event.getLoggerName())
                    .append(' ')
                    .append(event.getFormattedMessage())
                    .append('\n');
            for (Map.Entry<String, String> entry : event.getMDCPropertyMap().entrySet()) {
                text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            if (event.getKeyValuePairs() != null) {
                event.getKeyValuePairs()
                        .forEach(
                                pair ->
                                        text.append(pair.key)
                                                .append('=')
                                                .append(pair.value)
                                                .append('\n'));
            }
            if (event.getThrowableProxy() != null) {
                text.append(ThrowableProxyUtil.asString(event.getThrowableProxy())).append('\n');
            }
        }
        return text.toString();
    }

    public void clear() {
        appender.list.clear();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        root.detachAppender(appender);
        appender.stop();
        root.setLevel(rootLevel);
        application.setLevel(applicationLevel);
    }
}
