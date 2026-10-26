package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.port.CpfKeyCollisionException;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Saída e códigos de saída do comando, sem banco: o caso de uso real sobre uma porta de mentira.
 */
class RotateCpfKeyCommandTest {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    private final List<CpfKeyRotationPort.Options> received = new ArrayList<>();
    private final List<String> transactions = new ArrayList<>();
    private RuntimeException failure;

    private final TransactionPort transaction =
            new TransactionPort() {
                @Override
                public <T> T run(Supplier<T> excerpt) {
                    transactions.add("run");
                    return excerpt.get();
                }

                @Override
                public <T> T runAndRollBack(Supplier<T> excerpt) {
                    transactions.add("rollback");
                    return excerpt.get();
                }
            };

    private RotateCpfKeyCommand command(String... variables) {
        MockEnvironment environment = new MockEnvironment();
        for (String variable : variables) {
            environment.setProperty(variable, "any-value");
        }
        CpfKeyRotationPort port =
                options -> {
                    received.add(options);
                    if (failure != null) {
                        throw failure;
                    }
                    return new CpfKeyRotationPort.Report(
                            4, options.rotateDigestSecret() ? 2 : 0, 3);
                };
        return new RotateCpfKeyCommand(new RotateCpfKeysUseCase(port, transaction), environment);
    }

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void withoutNewKeysNothingRunsAndTheVariablesAreNamed() {
        int code = command().run(List.of("rotate-cpf-key"), output);

        assertThat(code).isEqualTo(2);
        assertThat(text())
                .contains("ROTATE_NEW_CPF_ENCRYPTION_KEY")
                .contains("ROTATE_NEW_HMAC_SECRET");
        assertThat(received).isEmpty();
    }

    @Test
    void blankVariableCountsAsNotInformed() {
        MockEnvironment environment =
                new MockEnvironment().withProperty(RotateCpfKeyCommand.NEW_HMAC_SECRET, "  ");
        RotateCpfKeyCommand command =
                new RotateCpfKeyCommand(
                        new RotateCpfKeysUseCase(
                                options -> new CpfKeyRotationPort.Report(0, 0, 0), transaction),
                        environment);

        assertThat(command.run(List.of("rotate-cpf-key"), output)).isEqualTo(2);
    }

    @Test
    void digestSecretWithoutConfirmationIsRefusedAfterWarningTheEffect() {
        int code =
                command(RotateCpfKeyCommand.NEW_HMAC_SECRET).run(List.of("rotate-cpf-key"), output);

        assertThat(code).isEqualTo(1);
        assertThat(text())
                .contains("invalida os códigos de acesso vigentes")
                .contains("AUTH_HMAC_SECRET")
                .contains("--confirm-invalidate-codes");
        assertThat(received).isEmpty();
    }

    @Test
    void confirmedRotationPrintsOnlyCounts() {
        int code =
                command(RotateCpfKeyCommand.NEW_ENCRYPTION_KEY, RotateCpfKeyCommand.NEW_HMAC_SECRET)
                        .run(List.of("rotate-cpf-key", "--confirm-invalidate-codes"), output);

        assertThat(code).isZero();
        assertThat(text())
                .contains("Rotação concluída")
                .contains("Alunos recifrados: 4")
                .contains("Códigos de acesso invalidados: 2")
                .contains("Rascunhos de cadastro do Telegram descartados: 3");
        assertThat(received).containsExactly(new CpfKeyRotationPort.Options(true, true, false));
        assertThat(transactions).containsExactly("run");
    }

    @Test
    void simulationRollsBackAndSaysNothingWasSaved() {
        int code =
                command(RotateCpfKeyCommand.NEW_HMAC_SECRET)
                        .run(List.of("rotate-cpf-key", "--simulate"), output);

        assertThat(code).isZero();
        assertThat(text()).contains("Simulação concluída: nada foi gravado.");
        assertThat(transactions).containsExactly("rollback");
    }

    @Test
    void collisionIsReportedWithTheCountOnly() {
        failure = new CpfKeyCollisionException(2);

        int code =
                command(RotateCpfKeyCommand.NEW_ENCRYPTION_KEY)
                        .run(List.of("rotate-cpf-key"), output);

        assertThat(code).isEqualTo(1);
        assertThat(text()).contains("2 colisão(ões)").contains("nada foi gravado");
    }

    @Test
    void invalidKeysAreReportedWithoutTheirValues() {
        failure = new IllegalStateException("Variável ROTATE_OLD_CPF_ENCRYPTION_KEY não informada");

        int code =
                command(RotateCpfKeyCommand.NEW_ENCRYPTION_KEY)
                        .run(List.of("rotate-cpf-key"), output);

        assertThat(code).isEqualTo(1);
        assertThat(text())
                .contains("Rotação desfeita: Variável ROTATE_OLD_CPF_ENCRYPTION_KEY")
                .doesNotContain("any-value");
    }
}
