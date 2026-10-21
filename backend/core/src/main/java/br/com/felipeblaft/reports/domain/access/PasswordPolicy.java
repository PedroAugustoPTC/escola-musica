package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.Secret;

/**
 * Política de senha (FR-004, data-model.md §5): 12 a 128 pontos de código em NFC, sem regra de
 * composição, e diferente do e-mail e do nome sem diferenciar caixa. Esqueleto da Fundação; a regra
 * é do fluxo W-A.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    /**
     * Valida e devolve a senha normalizada (NFC); recusa com {@code PASSWORD_LENGTH} ou {@code
     * PASSWORD_EQUALS_IDENTITY}.
     */
    public static Secret validate(Secret password, String email, String name) {
        throw new UnsupportedOperationException("PasswordPolicy: fluxo W-A");
    }
}
