package br.com.felipeblaft.reports.domain.access;

import java.util.Optional;

/**
 * Código de ativação do administrador (R-07): 12 caracteres do mesmo alfabeto sem ambíguos do
 * {@link AccessCode}, 7 dias, uso único. Só existe em claro na resposta que o gerou. Esqueleto da
 * Fundação; a regra é do fluxo W-A. O {@code toString} não pode conter o valor.
 */
public final class ActivationCode {

    private ActivationCode() {}

    /** Normaliza (sem espaço nem hífen, em maiúsculas) e valida; vazio se não for um código. */
    public static Optional<ActivationCode> retry(String input) {
        throw new UnsupportedOperationException("ActivationCode: fluxo W-A");
    }

    /** Texto com cara de código de ativação. */
    public static boolean looksLikeCode(String input) {
        throw new UnsupportedOperationException("ActivationCode: fluxo W-A");
    }

    public String value() {
        throw new UnsupportedOperationException("ActivationCode: fluxo W-A");
    }
}
