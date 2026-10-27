package br.com.felipeblaft.reports.domain.registry;

import java.util.Objects;

/**
 * CPF já protegido (cifrado e resumo com chave), pronto para gravar. Nunca contém o CPF em claro; o
 * {@code toString} não revela nem os bytes cifrados.
 */
public record ProtectedCpf(byte[] encrypted, String hash) {

    public ProtectedCpf {
        encrypted = Objects.requireNonNull(encrypted).clone();
        Objects.requireNonNull(hash);
    }

    @Override
    public byte[] encrypted() {
        return encrypted.clone();
    }

    @Override
    public String toString() {
        return "ProtectedCpf";
    }
}
