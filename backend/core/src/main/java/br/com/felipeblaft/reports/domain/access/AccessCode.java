package br.com.felipeblaft.reports.domain.access;

import java.util.Locale;
import java.util.Optional;

/**
 * Código de acesso do professor: 8 caracteres do alfabeto sem caracteres ambíguos (FR-004, R-12).
 * Só existe em claro na saída da carga inicial; no banco fica apenas o HMAC.
 */
public final class AccessCode {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int SIZE = 8;

    private final String value;

    private AccessCode(String value) {
        this.value = value;
    }

    /**
     * Normaliza (sem espaços nem hífens, em maiúsculas) e valida o formato; vazio se não for um
     * código possível. Aceita a forma agrupada mostrada ao professor (ex.: {@code K7QM-2XPA}).
     */
    public static Optional<AccessCode> retry(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (normalized.length() != SIZE) {
            return Optional.empty();
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (ALPHABET.indexOf(normalized.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new AccessCode(normalized));
    }

    /** Texto com cara de código (6 a 10 letras ou dígitos): conta como tentativa no Telegram. */
    public static boolean looksLikeCode(String input) {
        return input != null && input.replaceAll("[\\s-]", "").matches("[A-Za-z0-9]{6,10}");
    }

    public static AccessCode of(String input) {
        return retry(input)
                .orElseThrow(
                        () -> new IllegalArgumentException("código de acesso fora do formato"));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AccessCode code && code.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "AccessCode[***]";
    }
}
