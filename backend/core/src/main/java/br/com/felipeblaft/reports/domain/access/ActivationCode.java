package br.com.felipeblaft.reports.domain.access;

import java.util.Locale;
import java.util.Optional;

/**
 * Código de ativação do administrador (R-07): 12 caracteres do mesmo alfabeto sem ambíguos do
 * {@link AccessCode}, 7 dias, uso único. Só existe em claro na resposta que o gerou; no banco fica
 * apenas o HMAC. O {@code toString} e as mensagens de erro nunca contêm o valor.
 */
public final class ActivationCode {

    public static final String ALPHABET = AccessCode.ALPHABET;
    public static final int SIZE = 12;

    private final String value;

    private ActivationCode(String value) {
        this.value = value;
    }

    /**
     * Normaliza (sem espaço nem hífen, em maiúsculas) e valida; vazio se não for um código. Aceita
     * a forma agrupada mostrada ao administrador (ex.: {@code KQMX-PTRW-HNCV}).
     */
    public static Optional<ActivationCode> retry(String input) {
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
        return Optional.of(new ActivationCode(normalized));
    }

    /** Texto com cara de código de ativação (10 a 14 letras ou dígitos). */
    public static boolean looksLikeCode(String input) {
        return input != null && input.replaceAll("[\\s-]", "").matches("[A-Za-z0-9]{10,14}");
    }

    public static ActivationCode of(String input) {
        return retry(input)
                .orElseThrow(
                        () -> new IllegalArgumentException("código de ativação fora do formato"));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ActivationCode code && code.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "ActivationCode[***]";
    }
}
