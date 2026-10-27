package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.RegistryLimits;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Política de senha (FR-004, data-model.md §5): 12 a 128 pontos de código em NFC, sem regra de
 * composição, e diferente do e-mail e do nome sem diferenciar caixa. A recusa nunca repete a senha.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    /**
     * Valida e devolve a senha normalizada (NFC); recusa com {@code PASSWORD_LENGTH} ou {@code
     * PASSWORD_EQUALS_IDENTITY}. {@code email} e {@code name} nulos dispensam a comparação com a
     * identidade (quando a conta ainda não é conhecida).
     */
    public static Secret validate(Secret password, String email, String name) {
        if (password == null) {
            throw new DomainException(ErrorCode.PASSWORD_LENGTH);
        }
        String normalized = Normalizer.normalize(password.value(), Normalizer.Form.NFC);
        if (!lengthAcceptable(normalized)) {
            throw new DomainException(ErrorCode.PASSWORD_LENGTH);
        }
        if (sameAs(normalized, email) || sameAs(normalized, name)) {
            throw new DomainException(ErrorCode.PASSWORD_EQUALS_IDENTITY);
        }
        return new Secret(normalized);
    }

    /** Dentro dos limites de tamanho (em pontos de código, após a NFC), sem as demais regras. */
    public static boolean acceptableLength(Secret password) {
        return password != null
                && lengthAcceptable(Normalizer.normalize(password.value(), Normalizer.Form.NFC));
    }

    private static boolean lengthAcceptable(String normalized) {
        int length = normalized.codePointCount(0, normalized.length());
        return length >= RegistryLimits.PASSWORD_MIN && length <= RegistryLimits.PASSWORD_MAX;
    }

    private static boolean sameAs(String password, String identity) {
        return identity != null && comparable(password).equals(comparable(identity));
    }

    private static String comparable(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
    }
}
