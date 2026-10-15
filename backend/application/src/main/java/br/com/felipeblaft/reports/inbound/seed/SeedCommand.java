package br.com.felipeblaft.reports.inbound.seed;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedFile;
import br.com.felipeblaft.reports.usecase.seed.SeedResult;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comando {@code carga --arquivo <arquivo.json> [--simular]} (contracts/carga-inicial.md). Mostra o
 * resumo sem CPF e, para cada professor novo, a linha {@code email → CÓDIGO (válido até …)}.
 */
@Component
@Profile("seed")
public class SeedCommand {

    public static final String NAME = "seed";

    private final LoadInitialRegistryUseCase useCase;
    private final MessageCatalog catalog;
    private final MessageFormatting formatting;
    private final JsonMapper json =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public SeedCommand(
            LoadInitialRegistryUseCase useCase,
            MessageCatalog catalog,
            MessageFormatting formatting) {
        this.useCase = useCase;
        this.catalog = catalog;
        this.formatting = formatting;
    }

    /** Devolve o código de saída do processo (0 = sucesso). */
    public int run(List<String> arguments, PrintStream output) {
        String file = CommandArguments.value(arguments, "--file");
        boolean simulate = arguments.contains("--simulate");
        if (file == null) {
            output.println("Uso: seed --file <arquivo.json> [--simulate]");
            return 2;
        }
        SeedFile content;
        try {
            content = json.readValue(Files.readString(Path.of(file)), SeedFile.class);
        } catch (Exception error) {
            output.println(
                    "Não foi possível ler o arquivo de carga (JSON inválido ou arquivo ausente).");
            return 1;
        }
        SeedResult result = useCase.run(content, simulate);
        print(result, output);
        return result.success() ? 0 : 1;
    }

    private void print(SeedResult result, PrintStream output) {
        if (!result.success()) {
            output.println("Carga recusada: nada foi gravado.");
            for (SeedResult.SeedError error : result.errors()) {
                output.printf(
                        "%s[%d].%s: %s — %s%n",
                        error.section(),
                        error.index(),
                        error.field(),
                        error.code().name(),
                        catalog.text(error.code().messageKey()));
            }
            return;
        }
        output.println(
                result.simulation()
                        ? "Simulação concluída: nada foi gravado."
                        : "Carga concluída.");
        output.printf(
                "Professores: %d criados, %d atualizados, %d inalterados%n",
                result.teachers().created(),
                result.teachers().updated(),
                result.teachers().unchanged());
        output.printf(
                "Alunos: %d criados, %d atualizados, %d inalterados%n",
                result.students().created(),
                result.students().updated(),
                result.students().unchanged());
        output.printf("Associações criadas: %d%n", result.createdAssignments());
        if (!result.issuedCodes().isEmpty()) {
            output.println("Códigos emitidos (repasse cada código ao professor fora do sistema):");
            for (SeedResult.IssuedCode code : result.issuedCodes()) {
                output.println(CommandArguments.codeLine(code, formatting));
            }
        }
    }
}
