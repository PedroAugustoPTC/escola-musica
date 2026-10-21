package br.com.felipeblaft.reports.domain.registry;

/**
 * E-mail validado (R-11): até 254 caracteres, formato básico, guardado em minúsculas. Esqueleto da
 * Fundação; a regra é do fluxo W-C1. O {@code toString} não pode conter o e-mail.
 */
public final class EmailAddress {

    private EmailAddress() {}

    /** Valida e normaliza; recusa com {@code INVALID_EMAIL}. */
    public static EmailAddress of(String text) {
        throw new UnsupportedOperationException("EmailAddress: fluxo W-C1");
    }

    public String value() {
        throw new UnsupportedOperationException("EmailAddress: fluxo W-C1");
    }
}
