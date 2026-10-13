package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import br.com.felipeblaft.reports.service.CodeAuthenticationService;
import java.util.Objects;

/**
 * Vincula a conta do Telegram enviando o mesmo código da Web (FR-004, FR-006). Uma conta só se
 * vincula a um professor e um professor a uma conta. O código não é consumido.
 */
public class LinkTelegramUseCase {

    public record Result(Teacher teacher, TelegramLink link) {}

    private final CodeAuthenticationService authentication;
    private final AccessRenewalService renewal;
    private final TelegramLinkRepository links;
    private final ClockPort clock;

    public LinkTelegramUseCase(
            CodeAuthenticationService authentication,
            AccessRenewalService renewal,
            TelegramLinkRepository links,
            ClockPort clock) {
        this.authentication = Objects.requireNonNull(authentication);
        this.renewal = Objects.requireNonNull(renewal);
        this.links = Objects.requireNonNull(links);
        this.clock = Objects.requireNonNull(clock);
    }

    public Result run(long telegramUserId, long chatId, String code) {
        Teacher teacher =
                authentication.authenticate(Channel.TELEGRAM, Long.toString(telegramUserId), code);
        if (links.searchByTelegramUserId(telegramUserId).isPresent()) {
            throw new DomainException(ErrorCode.TELEGRAM_JA_VINCULADO);
        }
        if (links.searchByTeacherId(teacher.id()).isPresent()) {
            throw new DomainException(ErrorCode.PROFESSOR_JA_VINCULADO);
        }
        TelegramLink link = TelegramLink.create(teacher.id(), telegramUserId, chatId, clock.now());
        links.save(link);
        renewal.renew(teacher);
        return new Result(teacher, link);
    }
}
