package br.com.felipeblaft.reports.port;

/** Áudio sem fala, vazio ou recusado pelo provedor: nada é criado (FR-017). */
public class AudioNotProcessableException extends RuntimeException {

    public enum Reason {
        NO_SPEECH,
        FORMAT,
        EMPTY
    }

    private final Reason reason;

    public AudioNotProcessableException(Reason reason) {
        super(reason.name(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
