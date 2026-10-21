package br.com.felipeblaft.reports.domain.registry;

import java.time.LocalDate;

/**
 * Data de nascimento validada (R-11): existe no calendário, não é futura nem anterior a 120 anos,
 * em relação à data de hoje no fuso da escola. Esqueleto da Fundação; a regra é do fluxo W-C1. O
 * {@code toString} não pode conter a data.
 */
public final class BirthDate {

    private BirthDate() {}

    /**
     * Interpreta {@code aaaa-mm-dd} (Web) ou {@code dd/mm/aaaa} (Telegram) e valida contra {@code
     * today}; recusa com {@code INVALID_BIRTH_DATE}.
     */
    public static BirthDate parse(String text, LocalDate today) {
        throw new UnsupportedOperationException("BirthDate: fluxo W-C1");
    }

    public LocalDate value() {
        throw new UnsupportedOperationException("BirthDate: fluxo W-C1");
    }

    /** Idade em anos completos na data de referência; 18 anos completos é adulto. */
    public int ageAt(LocalDate reference) {
        throw new UnsupportedOperationException("BirthDate: fluxo W-C1");
    }
}
