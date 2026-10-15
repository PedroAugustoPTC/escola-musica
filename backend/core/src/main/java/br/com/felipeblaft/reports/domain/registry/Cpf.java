package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;

/**
 * CPF normalizado (11 dígitos, dígitos verificadores válidos). Nunca é exibido nem registrado: o
 * {@link #toString()} não revela o valor (FR-011, FR-055).
 */
public final class Cpf {

    private final String digits;

    private Cpf(String digits) {
        this.digits = digits;
    }

    public static Cpf of(String input) {
        if (input == null) {
            throw new DomainException(ErrorCode.INVALID_CPF);
        }
        String digits = input.replaceAll("[.\\-\\s/]", "");
        if (!digits.matches("\\d{11}") || digits.chars().distinct().count() == 1) {
            throw new DomainException(ErrorCode.INVALID_CPF);
        }
        if (checkDigit(digits, 9) != digits.charAt(9) - '0'
                || checkDigit(digits, 10) != digits.charAt(10) - '0') {
            throw new DomainException(ErrorCode.INVALID_CPF);
        }
        return new Cpf(digits);
    }

    private static int checkDigit(String digits, int quantity) {
        int sum = 0;
        for (int i = 0; i < quantity; i++) {
            sum += (digits.charAt(i) - '0') * (quantity + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    /** Os 11 dígitos, apenas para cifragem e hash. */
    public String digits() {
        return digits;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Cpf cpf && cpf.digits.equals(digits);
    }

    @Override
    public int hashCode() {
        return digits.hashCode();
    }

    @Override
    public String toString() {
        return "Cpf[***]";
    }
}
