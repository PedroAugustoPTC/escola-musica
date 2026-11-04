package br.com.felipeblaft.reports.config.log;

import org.slf4j.MDC;

/**
 * Põe o identificador de correlação da autoria no contexto dos logs (FR-050): toda linha emitida
 * enquanto o escopo está aberto leva {@code correlationId} igual ao do registro de autoria da mesma
 * requisição (Web: UUID da requisição; Telegram: {@code update_id}). O canal da requisição também
 * fica no contexto, para a trilha de auditoria. Use em try-com-recursos.
 */
public final class CorrelationMdc implements AutoCloseable {

    /** Chave no MDC e no JSON dos logs. */
    public static final String KEY = "correlationId";

    /** Canal da requisição ({@code WEB} ou {@code TELEGRAM}); lido pela trilha de auditoria. */
    public static final String ORIGIN_KEY = "origin";

    private final String previous;
    private final String previousOrigin;

    private CorrelationMdc(String previous, String previousOrigin) {
        this.previous = previous;
        this.previousOrigin = previousOrigin;
    }

    public static CorrelationMdc open(String correlationId) {
        return open(correlationId, null);
    }

    /** Abre o escopo com o identificador de correlação e o canal da requisição. */
    public static CorrelationMdc open(String correlationId, String origin) {
        String previous = MDC.get(KEY);
        String previousOrigin = MDC.get(ORIGIN_KEY);
        MDC.put(KEY, correlationId);
        if (origin != null) {
            MDC.put(ORIGIN_KEY, origin);
        }
        return new CorrelationMdc(previous, previousOrigin);
    }

    @Override
    public void close() {
        restore(KEY, previous);
        restore(ORIGIN_KEY, previousOrigin);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
