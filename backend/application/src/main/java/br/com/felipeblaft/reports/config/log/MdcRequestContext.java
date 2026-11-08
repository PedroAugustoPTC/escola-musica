package br.com.felipeblaft.reports.config.log;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.port.RequestContextPort;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Contexto da requisição lido do MDC, que o filtro de correlação (Web) e a entrada do Telegram
 * abrem no começo de cada requisição ou update. Fora de uma requisição não há contexto, e quem o
 * pede recebe erro (a trilha não aceita um evento sem canal nem correlação).
 */
@Component
class MdcRequestContext implements RequestContextPort {

    @Override
    public boolean active() {
        return MDC.get(CorrelationMdc.KEY) != null && MDC.get(CorrelationMdc.ORIGIN_KEY) != null;
    }

    @Override
    public ActionOrigin origin() {
        String origin = MDC.get(CorrelationMdc.ORIGIN_KEY);
        if (origin == null) {
            throw new IllegalStateException("Contexto da requisição ausente");
        }
        return ActionOrigin.valueOf(origin);
    }

    @Override
    public String correlationId() {
        String id = MDC.get(CorrelationMdc.KEY);
        if (id == null) {
            throw new IllegalStateException("Contexto da requisição ausente");
        }
        return id;
    }
}
