package br.com.felipeblaft.reports.usecase.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.port.CpfKeyCollisionException;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import br.com.felipeblaft.reports.usecase.seed.RegistryUseCaseDoubles.RecordingTransaction;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase.Command;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase.RefusalReason;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase.RotationRefusedException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rotação das chaves do CPF (FR-020): o caso de uso só orquestra a porta e devolve contagens. */
class RotateCpfKeysUseCaseTest {

    private final RecordingTransaction transaction = new RecordingTransaction();
    private final List<CpfKeyRotationPort.Options> received = new ArrayList<>();
    private RuntimeException failure;

    private final CpfKeyRotationPort rotation =
            options -> {
                received.add(options);
                if (failure != null) {
                    throw failure;
                }
                return new CpfKeyRotationPort.Report(7, options.rotateDigestSecret() ? 2 : 0, 1);
            };

    private final RotateCpfKeysUseCase useCase = new RotateCpfKeysUseCase(rotation, transaction);

    @Test
    void encryptionKeyOnlyRotatesInATransactionWithoutInvalidatingCodes() {
        CpfKeyRotationPort.Report report = useCase.run(new Command(true, false, false, false));

        assertThat(report).isEqualTo(new CpfKeyRotationPort.Report(7, 0, 1));
        assertThat(received).containsExactly(new CpfKeyRotationPort.Options(true, false, false));
        assertThat(transaction.committed).isEqualTo(1);
        assertThat(transaction.rolledBack).isZero();
    }

    @Test
    void digestSecretRequiresTheExplicitConfirmationBeforeAnythingRuns() {
        assertThatThrownBy(() -> useCase.run(new Command(false, true, false, false)))
                .isInstanceOfSatisfying(
                        RotationRefusedException.class,
                        refusal ->
                                assertThat(refusal.reason())
                                        .isEqualTo(RefusalReason.CONFIRMATION_REQUIRED));
        assertThat(received).isEmpty();
        assertThat(transaction.committed).isZero();
    }

    @Test
    void confirmedDigestSecretRotatesAndReportsTheInvalidatedCodes() {
        CpfKeyRotationPort.Report report = useCase.run(new Command(true, true, false, true));

        assertThat(report).isEqualTo(new CpfKeyRotationPort.Report(7, 2, 1));
        assertThat(received).containsExactly(new CpfKeyRotationPort.Options(true, true, false));
    }

    @Test
    void simulationCountsInATransactionThatIsRolledBackEvenWithoutConfirmation() {
        CpfKeyRotationPort.Report report = useCase.run(new Command(false, true, true, false));

        assertThat(report).isEqualTo(new CpfKeyRotationPort.Report(7, 2, 1));
        assertThat(received).containsExactly(new CpfKeyRotationPort.Options(false, true, true));
        assertThat(transaction.rolledBack).isEqualTo(1);
        assertThat(transaction.committed).isZero();
    }

    @Test
    void nothingToRotateIsRefused() {
        assertThatThrownBy(() -> useCase.run(new Command(false, false, false, true)))
                .isInstanceOfSatisfying(
                        RotationRefusedException.class,
                        refusal ->
                                assertThat(refusal.reason())
                                        .isEqualTo(RefusalReason.NOTHING_TO_ROTATE));
        assertThat(received).isEmpty();
    }

    @Test
    void collisionAfterRotationUndoesEverythingAndCarriesOnlyTheCount() {
        failure = new CpfKeyCollisionException(3);

        assertThatThrownBy(() -> useCase.run(new Command(true, true, false, true)))
                .isInstanceOfSatisfying(
                        CpfKeyCollisionException.class,
                        collision -> {
                            assertThat(collision.collisions()).isEqualTo(3);
                            assertThat(collision.getMessage()).doesNotContainPattern("[0-9]{11}");
                        });
        assertThat(transaction.rolledBack).isEqualTo(1);
        assertThat(transaction.committed).isZero();
    }
}
