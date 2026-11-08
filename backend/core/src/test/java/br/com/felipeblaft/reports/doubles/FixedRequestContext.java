package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.port.RequestContextPort;

/** Contexto de requisição controlado pelo teste: canal e identificador de correlação fixos. */
public class FixedRequestContext implements RequestContextPort {

    private ActionOrigin origin = ActionOrigin.WEB;
    private String correlationId = "req-test";
    private boolean active = true;

    /** Simula a rotina de fundo, sem requisição em curso. */
    public void deactivate() {
        this.active = false;
    }

    @Override
    public boolean active() {
        return active;
    }

    public void define(ActionOrigin origin, String correlationId) {
        this.origin = origin;
        this.correlationId = correlationId;
    }

    @Override
    public ActionOrigin origin() {
        return origin;
    }

    @Override
    public String correlationId() {
        return correlationId;
    }
}
