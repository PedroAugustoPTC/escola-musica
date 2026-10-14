package br.com.felipeblaft.reports.inbound.seed;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.usecase.seed.EmitAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import java.io.PrintStream;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Comando {@code emitir-codigo --email <email>}: reemite o código de um professor. */
@Component
@Profile("seed")
public class IssueCodeCommand {

    public static final String NAME = "issue-code";

    private final EmitAccessCodeUseCase useCase;
    private final MessageCatalog catalog;
    private final MessageFormatting formatting;

    public IssueCodeCommand(
            EmitAccessCodeUseCase useCase, MessageCatalog catalog, MessageFormatting formatting) {
        this.useCase = useCase;
        this.catalog = catalog;
        this.formatting = formatting;
    }

    public int run(List<String> arguments, PrintStream output) {
        String email = CommandArguments.value(arguments, "--email");
        if (email == null) {
            output.println("Uso: issue-code --email <email-do-professor>");
            return 2;
        }
        try {
            IssuedCode code = useCase.run(email);
            output.println(CommandArguments.codeLine(code, formatting));
            return 0;
        } catch (DomainException error) {
            output.println(catalog.text(error.code().messageKey()));
            return 1;
        }
    }
}
