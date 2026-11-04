package br.com.felipeblaft.reports.domain.privacy;

import java.util.Objects;

/**
 * Valor efetivo de um parâmetro numérico com a origem dele (mostrada na tela de prazos efetivos).
 */
public record PolicyValue(int value, PolicyOrigin origin) {

    public PolicyValue {
        Objects.requireNonNull(origin);
    }

    public static PolicyValue ofDefault(int value) {
        return new PolicyValue(value, PolicyOrigin.DEFAULT);
    }
}
