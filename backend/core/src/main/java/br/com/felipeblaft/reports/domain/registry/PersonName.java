package br.com.felipeblaft.reports.domain.registry;

/**
 * Nome de pessoa validado (R-11): corte das pontas, NFC, 2 a 120 caracteres, sem caracteres de
 * controle. Esqueleto da Fundação; a regra é do fluxo W-C1. O {@code toString} não pode conter o
 * nome.
 */
public final class PersonName {

    private PersonName() {}

    /** Valida e normaliza; recusa com {@code INVALID_NAME} ou {@code INVALID_TEXT}. */
    public static PersonName of(String text) {
        throw new UnsupportedOperationException("PersonName: fluxo W-C1");
    }

    public String value() {
        throw new UnsupportedOperationException("PersonName: fluxo W-C1");
    }
}
