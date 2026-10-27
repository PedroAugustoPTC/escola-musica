package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Data de nascimento validada (R-11): existe no calendário, não é futura nem anterior a 120 anos,
 * em relação à data de hoje no fuso da escola. O {@code toString} não contém a data.
 */
public final class BirthDate {

    private static final DateTimeFormatter WEB =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TELEGRAM =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final LocalDate value;

    private BirthDate(LocalDate value) {
        this.value = value;
    }

    /**
     * Interpreta {@code aaaa-mm-dd} (Web) ou {@code dd/mm/aaaa} (Telegram) e valida contra {@code
     * today}; recusa com {@code INVALID_BIRTH_DATE}.
     */
    public static BirthDate parse(String text, LocalDate today) {
        LocalDate date = read(text == null ? "" : text.strip());
        if (date.isAfter(today)
                || date.isBefore(today.minusYears(RegistryLimits.BIRTH_MAX_AGE_YEARS))) {
            throw new DomainException(ErrorCode.INVALID_BIRTH_DATE);
        }
        return new BirthDate(date);
    }

    private static LocalDate read(String text) {
        try {
            return LocalDate.parse(text, text.contains("/") ? TELEGRAM : WEB);
        } catch (DateTimeParseException error) {
            throw new DomainException(ErrorCode.INVALID_BIRTH_DATE);
        }
    }

    public LocalDate value() {
        return value;
    }

    /** Idade em anos completos na data de referência (nunca negativa). */
    public int ageAt(LocalDate reference) {
        return Math.max(0, Period.between(value, reference).getYears());
    }

    /** 18 anos completos é adulto. */
    public boolean isAdultAt(LocalDate reference) {
        return ageAt(reference) >= RegistryLimits.ADULT_AGE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BirthDate date && date.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "BirthDate";
    }
}
