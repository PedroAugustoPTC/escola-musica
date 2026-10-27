package br.com.felipeblaft.reports.usecase.seed;

import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import java.util.Objects;

/**
 * Rotação das chaves do CPF (comando {@code rotate-cpf-key}, FR-020): orquestra a porta numa
 * transação, com {@code --simulate} por reversão, e devolve só contagens. Trocar o segredo do
 * resumo invalida os códigos de acesso vigentes e exige a confirmação explícita (a simulação não
 * grava, então dispensa a confirmação). Uma colisão de resumos desfaz a transação.
 */
public class RotateCpfKeysUseCase {

    public record Command(
            boolean rotateEncryptionKey,
            boolean rotateDigestSecret,
            boolean simulate,
            boolean confirmInvalidateCodes) {}

    /** Motivo da recusa de uma rotação que nem chega a começar. */
    public enum RefusalReason {
        NOTHING_TO_ROTATE,
        CONFIRMATION_REQUIRED
    }

    /** Recusa antes de qualquer alteração; traz só o motivo. */
    public static class RotationRefusedException extends RuntimeException {

        private final transient RefusalReason reason;

        public RotationRefusedException(RefusalReason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }

        public RefusalReason reason() {
            return reason;
        }
    }

    private final CpfKeyRotationPort rotation;
    private final TransactionPort transaction;

    public RotateCpfKeysUseCase(CpfKeyRotationPort rotation, TransactionPort transaction) {
        this.rotation = Objects.requireNonNull(rotation);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public CpfKeyRotationPort.Report run(Command command) {
        if (!command.rotateEncryptionKey() && !command.rotateDigestSecret()) {
            throw new RotationRefusedException(RefusalReason.NOTHING_TO_ROTATE);
        }
        if (command.rotateDigestSecret()
                && !command.simulate()
                && !command.confirmInvalidateCodes()) {
            throw new RotationRefusedException(RefusalReason.CONFIRMATION_REQUIRED);
        }
        CpfKeyRotationPort.Options options =
                new CpfKeyRotationPort.Options(
                        command.rotateEncryptionKey(),
                        command.rotateDigestSecret(),
                        command.simulate());
        return command.simulate()
                ? transaction.runAndRollBack(() -> rotation.rotate(options))
                : transaction.run(() -> rotation.rotate(options));
    }
}
