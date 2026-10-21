package br.com.felipeblaft.reports.usecase.seed;

import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import br.com.felipeblaft.reports.port.TransactionPort;

/**
 * Rotação das chaves do CPF (comando {@code rotate-cpf-key}, FR-020): orquestra a porta numa
 * transação, com {@code --simulate} por reversão, e devolve só contagens. Trocar o segredo do
 * resumo invalida os códigos de acesso vigentes e exige a confirmação explícita. Esqueleto da
 * Fundação; a regra é do fluxo W-K.
 */
public class RotateCpfKeysUseCase {

    public record Command(
            boolean rotateEncryptionKey,
            boolean rotateDigestSecret,
            boolean simulate,
            boolean confirmInvalidateCodes) {}

    public RotateCpfKeysUseCase(CpfKeyRotationPort rotation, TransactionPort transaction) {}

    public CpfKeyRotationPort.Report run(Command command) {
        throw new UnsupportedOperationException("RotateCpfKeysUseCase: fluxo W-K");
    }
}
