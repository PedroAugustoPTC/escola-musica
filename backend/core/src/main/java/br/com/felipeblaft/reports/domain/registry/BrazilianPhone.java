package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;

/**
 * Telefone brasileiro validado (R-11): aceita máscara, guarda só os dígitos, DDD de 11 a 99 e 10
 * (fixo) ou 11 (celular, começa com 9) dígitos. O {@code toString} não contém o número.
 */
public final class BrazilianPhone {

    private final String digits;

    private BrazilianPhone(String digits) {
        this.digits = digits;
    }

    /** Valida e normaliza; recusa com {@code INVALID_PHONE}. */
    public static BrazilianPhone of(String text) {
        if (text == null || !text.matches("[0-9\\s().\\-]*")) {
            throw new DomainException(ErrorCode.INVALID_PHONE);
        }
        String digits = text.replaceAll("[^0-9]", "");
        int size = digits.length();
        if (size != RegistryLimits.PHONE_DIGITS_LANDLINE
                && size != RegistryLimits.PHONE_DIGITS_MOBILE) {
            throw new DomainException(ErrorCode.INVALID_PHONE);
        }
        int areaCode = Integer.parseInt(digits.substring(0, 2));
        boolean mobileOk = size == RegistryLimits.PHONE_DIGITS_LANDLINE || digits.charAt(2) == '9';
        if (areaCode < RegistryLimits.DDD_MIN || areaCode > RegistryLimits.DDD_MAX || !mobileOk) {
            throw new DomainException(ErrorCode.INVALID_PHONE);
        }
        return new BrazilianPhone(digits);
    }

    /** Somente os dígitos. */
    public String digits() {
        return digits;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BrazilianPhone phone && phone.digits.equals(digits);
    }

    @Override
    public int hashCode() {
        return digits.hashCode();
    }

    @Override
    public String toString() {
        return "BrazilianPhone";
    }
}
