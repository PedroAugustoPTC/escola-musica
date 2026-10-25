package br.com.felipeblaft.reports.inbound.seed;

import br.com.felipeblaft.reports.port.CpfKeyCollisionException;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase.RefusalReason;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase.RotationRefusedException;
import java.io.PrintStream;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Comando {@code rotate-cpf-key [--simulate] [--confirm-invalidate-codes]} (FR-020,
 * contracts/initial-load.md §5). As chaves vêm de variáveis de ambiente, nunca de argumentos (não
 * ficam no histórico do shell): {@code ROTATE_NEW_CPF_ENCRYPTION_KEY} troca a chave de cifra e
 * {@code ROTATE_NEW_HMAC_SECRET} troca o segredo do resumo; cada uma é opcional e independente. A
 * saída traz só contagens, nunca CPF. Exige a janela de manutenção e uma cópia de segurança
 * recente.
 */
@Component
@Profile("seed")
public class RotateCpfKeyCommand {

    public static final String NAME = "rotate-cpf-key";

    static final String NEW_ENCRYPTION_KEY = "ROTATE_NEW_CPF_ENCRYPTION_KEY";
    static final String NEW_HMAC_SECRET = "ROTATE_NEW_HMAC_SECRET";

    private final RotateCpfKeysUseCase useCase;
    private final Environment environment;

    public RotateCpfKeyCommand(RotateCpfKeysUseCase useCase, Environment environment) {
        this.useCase = useCase;
        this.environment = environment;
    }

    public int run(List<String> arguments, PrintStream output) {
        boolean rotateEncryptionKey = informed(NEW_ENCRYPTION_KEY);
        boolean rotateDigestSecret = informed(NEW_HMAC_SECRET);
        boolean simulate = arguments.contains("--simulate");
        if (rotateDigestSecret) {
            output.println(
                    "Atenção: trocar o segredo do resumo (HMAC_SECRET) invalida os códigos de acesso"
                            + " vigentes dos professores; o administrador os reemite. Os códigos de"
                            + " ativação e as origens de tentativas usam o AUTH_HMAC_SECRET e não"
                            + " mudam.");
        }
        try {
            CpfKeyRotationPort.Report report =
                    useCase.run(
                            new RotateCpfKeysUseCase.Command(
                                    rotateEncryptionKey,
                                    rotateDigestSecret,
                                    simulate,
                                    arguments.contains("--confirm-invalidate-codes")));
            output.println(
                    simulate
                            ? "Simulação concluída: nada foi gravado."
                            : "Rotação concluída. Reinicie o serviço com as chaves novas.");
            output.printf("Alunos recifrados: %d%n", report.rotatedStudents());
            output.printf("Códigos de acesso invalidados: %d%n", report.invalidatedAccessCodes());
            return 0;
        } catch (RotationRefusedException refusal) {
            return refuse(refusal.reason(), output);
        } catch (CpfKeyCollisionException collision) {
            output.printf(
                    "Rotação desfeita: %d colisão(ões) de resumo de CPF; nada foi gravado.%n",
                    collision.collisions());
            return 1;
        } catch (IllegalStateException invalidKeys) {
            output.println("Rotação desfeita: " + invalidKeys.getMessage() + ". Nada foi gravado.");
            return 1;
        }
    }

    private static int refuse(RefusalReason reason, PrintStream output) {
        if (reason == RefusalReason.NOTHING_TO_ROTATE) {
            output.println(
                    "Nenhuma chave nova informada: defina "
                            + NEW_ENCRYPTION_KEY
                            + " e/ou "
                            + NEW_HMAC_SECRET
                            + " no ambiente.");
            return 2;
        }
        output.println(
                "Nada foi alterado. Para trocar o segredo do resumo, repita o comando com"
                        + " --confirm-invalidate-codes.");
        return 1;
    }

    private boolean informed(String variable) {
        String value = environment.getProperty(variable);
        return value != null && !value.isBlank();
    }
}
