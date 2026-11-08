package br.com.felipeblaft.reports.domain.privacy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Leitura estrita de datas de consentimento: {@code aaaa-mm-dd} ou {@code dd/mm/aaaa}. */
public final class ConsentDates {

    private static final DateTimeFormatter WEB =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TELEGRAM =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private ConsentDates() {}

    /** Data válida e não futura, ou {@code null}. */
    public static LocalDate parsePast(String text, LocalDate today) {
        if (text == null) {
            return null;
        }
        String clean = text.strip();
        try {
            LocalDate date = LocalDate.parse(clean, clean.contains("/") ? TELEGRAM : WEB);
            return date.isAfter(today) ? null : date;
        } catch (DateTimeParseException error) {
            return null;
        }
    }
}
