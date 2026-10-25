package br.com.felipeblaft.reports.inbound.seed;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import br.com.felipeblaft.reports.usecase.teacher.ReissueAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.teacher.TeacherWithCode;
import java.io.PrintStream;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Comando {@code issue-code --email <email>}: reemite o código de um professor pelo mesmo caso de
 * uso da administração ({@link ReissueAccessCodeUseCase}), com o ator de sistema da carga inicial.
 * Encerra as sessões Web do professor e <strong>não</strong> encerra o vínculo do Telegram. É
 * caminho de emergência (FR-044); o normal é o administrador reemitir pela Web.
 */
@Component
@Profile("seed")
public class IssueCodeCommand {

    public static final String NAME = "issue-code";

    /** Identificador de correlação da reemissão pelo terminal (formato aceito pela autoria). */
    private static final String CORRELATION_ID = "load-reissue";

    private final ReissueAccessCodeUseCase useCase;
    private final TeacherRepository teachers;
    private final MessageCatalog catalog;
    private final MessageFormatting formatting;

    public IssueCodeCommand(
            ReissueAccessCodeUseCase useCase,
            TeacherRepository teachers,
            MessageCatalog catalog,
            MessageFormatting formatting) {
        this.useCase = useCase;
        this.teachers = teachers;
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
            Teacher teacher =
                    teachers.searchByEmail(Teacher.normalizeEmail(email))
                            .orElseThrow(() -> new DomainException(ErrorCode.TEACHER_NOT_FOUND));
            TeacherWithCode issued =
                    useCase.run(
                            Actor.system(ActionOrigin.INITIAL_LOAD),
                            new ReissueAccessCodeUseCase.Command(
                                    teacher.id(), false, CORRELATION_ID));
            output.println(
                    CommandArguments.codeLine(
                            new IssuedCode(
                                    issued.teacher().email(),
                                    issued.accessCode().value(),
                                    issued.validUntil()),
                            formatting));
            return 0;
        } catch (DomainException error) {
            output.println(
                    error.code().messageKey() == null
                            ? error.code().name()
                            : catalog.text(error.code().messageKey()));
            return 1;
        }
    }
}
