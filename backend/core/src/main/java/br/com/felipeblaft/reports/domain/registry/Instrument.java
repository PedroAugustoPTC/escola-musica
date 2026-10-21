package br.com.felipeblaft.reports.domain.registry;

/**
 * Instrumento (opcional): até 60 caracteres, sem caracteres de controle (R-11). Esqueleto da
 * Fundação; a regra é do fluxo W-C1.
 */
public final class Instrument {

    private Instrument() {}

    /** Valida e normaliza; recusa com {@code INVALID_INSTRUMENT}. */
    public static Instrument of(String text) {
        throw new UnsupportedOperationException("Instrument: fluxo W-C1");
    }

    public String value() {
        throw new UnsupportedOperationException("Instrument: fluxo W-C1");
    }
}
