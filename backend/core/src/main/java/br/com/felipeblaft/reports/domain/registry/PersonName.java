package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;

/**
 * Nome de pessoa validado (R-11): corte das pontas, NFC, 2 a 120 caracteres, sem caracteres de
 * controle. O {@code toString} não contém o nome.
 */
public final class PersonName {

    private final String value;

    private PersonName(String value) {
        this.value = value;
    }

    /** Valida e normaliza; recusa com {@code INVALID_NAME} ou {@code INVALID_TEXT}. */
    public static PersonName of(String text) {
        String clean = RegistryText.clean(text);
        if (clean == null
                || RegistryText.length(clean) < RegistryLimits.NAME_MIN
                || RegistryText.length(clean) > RegistryLimits.NAME_MAX) {
            throw new DomainException(ErrorCode.INVALID_NAME);
        }
        return new PersonName(clean);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PersonName name && name.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "PersonName";
    }
}
