package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;

/**
 * Instrumento (opcional no cadastro): até 60 caracteres, sem caracteres de controle (R-11). A
 * ausência é tratada por quem monta o {@code StudentData}; aqui, texto vazio é recusado.
 */
public final class Instrument {

    private final String value;

    private Instrument(String value) {
        this.value = value;
    }

    /** Valida e normaliza; recusa com {@code INVALID_INSTRUMENT} ou {@code INVALID_TEXT}. */
    public static Instrument of(String text) {
        String clean = RegistryText.clean(text);
        if (clean == null || RegistryText.length(clean) > RegistryLimits.INSTRUMENT_MAX) {
            throw new DomainException(ErrorCode.INVALID_INSTRUMENT);
        }
        return new Instrument(clean);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Instrument instrument && instrument.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "Instrument";
    }
}
