package br.com.felipeblaft.reports.port;

/**
 * Rotação das chaves do CPF (FR-020, contracts/initial-load.md §5): recifra {@code cpf_encrypted}
 * com a chave nova e recalcula {@code cpf_hash} com o segredo novo, em lotes e em transação, sem
 * nunca emitir o CPF em claro. As chaves antigas e novas chegam ao adaptador pelo ambiente; o
 * núcleo só recebe contagens.
 */
public interface CpfKeyRotationPort {

    /**
     * @param rotateEncryptionKey troca a chave de cifra (reescreve {@code cpf_encrypted})
     * @param rotateDigestSecret troca o segredo do resumo (recalcula {@code cpf_hash}; invalida os
     *     códigos de acesso vigentes)
     * @param simulate confere e desfaz
     */
    record Options(boolean rotateEncryptionKey, boolean rotateDigestSecret, boolean simulate) {}

    /** Contagens do que foi (ou seria) rotacionado; nunca CPF. */
    record Report(int rotatedStudents, int invalidatedAccessCodes) {}

    Report rotate(Options options);
}
