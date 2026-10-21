package br.com.felipeblaft.reports.domain.common;

import java.util.Objects;

/**
 * Valor secreto (senha, código em claro, token): vive só na memória durante a operação e nunca
 * aparece em {@code toString}, log, mensagem de erro nem registro de autoria (Princípio IV).
 */
public record Secret(String value) {

    public Secret {
        Objects.requireNonNull(value);
    }

    @Override
    public String toString() {
        return "Secret[***]";
    }
}
