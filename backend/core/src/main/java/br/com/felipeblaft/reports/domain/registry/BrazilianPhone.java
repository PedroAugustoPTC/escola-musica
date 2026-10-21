package br.com.felipeblaft.reports.domain.registry;

/**
 * Telefone brasileiro validado (R-11): aceita máscara, guarda só os dígitos, DDD de 11 a 99 e 10
 * (fixo) ou 11 (celular, começa com 9) dígitos. Esqueleto da Fundação; a regra é do fluxo W-C1. O
 * {@code toString} não pode conter o número.
 */
public final class BrazilianPhone {

    private BrazilianPhone() {}

    /** Valida e normaliza; recusa com {@code INVALID_PHONE}. */
    public static BrazilianPhone of(String text) {
        throw new UnsupportedOperationException("BrazilianPhone: fluxo W-C1");
    }

    /** Somente os dígitos. */
    public String digits() {
        throw new UnsupportedOperationException("BrazilianPhone: fluxo W-C1");
    }
}
