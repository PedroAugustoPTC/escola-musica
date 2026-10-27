package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.registry.Cpf;

/**
 * Decifra o CPF guardado no cadastro de aluno em andamento do Telegram, só no instante da
 * confirmação, para entregá-lo ao caso de uso que grava o aluno (o único que o recebe em claro). É
 * uma porta própria e estreita para que apenas a confirmação ganhe esse poder: o resto do núcleo
 * nunca lê um CPF de volta (FR-011, Princípio IV).
 */
public interface CpfDecryptionPort {

    /** Decifra o CPF cifrado por {@link DataProtectionPort#encryptCpf(Cpf)}. */
    Cpf decrypt(byte[] encrypted);
}
