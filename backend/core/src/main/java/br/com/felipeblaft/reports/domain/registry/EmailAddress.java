package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * E-mail validado (R-11): até 254 caracteres, formato básico, guardado em minúsculas. O {@code
 * toString} não contém o e-mail.
 */
public final class EmailAddress {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final String value;

    private EmailAddress(String value) {
        this.value = value;
    }

    /** Valida e normaliza; recusa com {@code INVALID_EMAIL} (ou {@code INVALID_TEXT}). */
    public static EmailAddress of(String text) {
        String clean = RegistryText.clean(text);
        if (clean == null
                || RegistryText.length(clean) > RegistryLimits.EMAIL_MAX
                || !FORMAT.matcher(clean).matches()) {
            throw new DomainException(ErrorCode.INVALID_EMAIL);
        }
        return new EmailAddress(clean.toLowerCase(Locale.ROOT));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmailAddress email && email.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "EmailAddress";
    }
}
