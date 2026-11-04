package br.com.felipeblaft.reports.inbound.seed;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * No perfil {@code seed}, executa o comando pedido na linha de comando e encerra o processo com o
 * código de saída do comando. Os argumentos de configuração do Spring ({@code --spring.*}) são
 * ignorados.
 */
@Component
@Profile("seed & !test")
class SeedCommandsExecutor implements ApplicationRunner {

    private final SeedCommand seed;
    private final IssueCodeCommand issueCode;
    private final DemoDataCommand demodata;
    private final RotateCpfKeyCommand rotateCpfKey;
    private final ConsentLoadCommand consentLoad;
    private final ApplicationContext context;

    SeedCommandsExecutor(
            SeedCommand seed,
            IssueCodeCommand issueCode,
            DemoDataCommand demodata,
            RotateCpfKeyCommand rotateCpfKey,
            ConsentLoadCommand consentLoad,
            ApplicationContext context) {
        this.consentLoad = consentLoad;
        this.seed = seed;
        this.issueCode = issueCode;
        this.demodata = demodata;
        this.rotateCpfKey = rotateCpfKey;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments applicationArguments) {
        List<String> arguments =
                List.of(applicationArguments.getSourceArgs()).stream()
                        .filter(argument -> !argument.startsWith("--spring."))
                        .toList();
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String command = arguments.isEmpty() ? "" : arguments.getFirst();
        int outputCode =
                switch (command) {
                    case SeedCommand.NAME -> seed.run(arguments, output);
                    case IssueCodeCommand.NAME -> issueCode.run(arguments, output);
                    case DemoDataCommand.NAME -> demodata.run(arguments, output);
                    case RotateCpfKeyCommand.NAME -> rotateCpfKey.run(arguments, output);
                    case ConsentLoadCommand.NAME -> consentLoad.run(arguments, output);
                    default -> {
                        output.println(
                                "Comandos: seed --file <arquivo.json> [--simulate] | issue-code --email <email>"
                                        + " | demo-data --file <cadastros.json>"
                                        + " | rotate-cpf-key [--simulate] [--confirm-invalidate-codes]"
                                        + " | consent-load --file <consentimentos.csv>");
                        yield 2;
                    }
                };
        System.exit(SpringApplication.exit(context, () -> outputCode));
    }
}
