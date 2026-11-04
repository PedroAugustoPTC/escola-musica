package br.com.felipeblaft.reports.config.log;

import org.slf4j.MDC;

/**
 * Põe o identificador de correlação da autoria no contexto dos logs (FR-050): toda linha emitida
 * enquanto o escopo está aberto leva {@code correlationId} igual ao do registro de autoria da mesma
 * requisição (Web: UUID da requisição; Telegram: {@code update_id}). Use em try-com-recursos.
 */
public final class CorrelationMdc implements AutoCloseable {

    /** Chave no MDC e no JSON dos logs. */
    public static final String KEY = "correlationId";

    private final String previous;

    private CorrelationMdc(String previous) {
        this.previous = previous;
    }

    public static CorrelationMdc open(String correlationId) {
        String previous = MDC.get(KEY);
        MDC.put(KEY, correlationId);
        return new CorrelationMdc(previous);
    }

    @Override
    public void close() {
        if (previous == null) {
            MDC.remove(KEY);
        } else {
            MDC.put(KEY, previous);
        }
    }
}
