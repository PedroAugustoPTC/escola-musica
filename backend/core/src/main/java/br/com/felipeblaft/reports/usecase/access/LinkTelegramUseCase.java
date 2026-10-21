package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.CodeAuthenticationService;
import br.com.felipeblaft.reports.service.TeacherAccessAuditService;
import java.util.Objects;

/**
 * Vincula a conta do Telegram enviando o mesmo código da Web (FR-004, FR-006). Uma conta só se
 * vincula a um professor e um professor a uma conta. O código não é consumido. Deixa o registro de
 * autoria do vínculo, com sucesso ou falha, e do bloqueio (FR-040).
 */
public class LinkTelegramUseCase {

    public record Result(Teacher teacher, TelegramLink link) {}

    private final CodeAuthenticationService authentication;
    private final AccessRenewalService renewal;
    private final TelegramLinkRepository links;
    private final TeacherAccessAuditService audit;
    private final ClockPort clock;

    public LinkTelegramUseCase(
            CodeAuthenticationService authentication,
            AccessRenewalService renewal,
            TelegramLinkRepository links,
            AuthorshipService authorship,
            DataProtectionPort protection,
            ClockPort clock) {
        this.authentication = Objects.requireNonNull(authentication);
        this.renewal = Objects.requireNonNull(renewal);
        this.links = Objects.requireNonNull(links);
        this.audit = new TeacherAccessAuditService(authorship, protection);
        this.clock = Objects.requireNonNull(clock);
    }

    /** {@code correlationId} é o {@code update_id} do Telegram, para o registro de autoria. */
    public Result run(long telegramUserId, long chatId, String code, String correlationId) {
        String origin = Long.toString(telegramUserId);
        Teacher teacher =
                audit.authenticate(
                        authentication,
                        Channel.TELEGRAM,
                        origin,
                        code,
                        AuthorshipRecord.TEACHER_LINK_TELEGRAM,
                        correlationId);
        if (links.searchByTelegramUserId(telegramUserId).isPresent()) {
            throw refused(origin, teacher, ErrorCode.TELEGRAM_ALREADY_LINKED, correlationId);
        }
        if (links.searchByTeacherId(teacher.id()).isPresent()) {
            throw refused(origin, teacher, ErrorCode.TEACHER_ALREADY_LINKED, correlationId);
        }
        // sem registro de autoria, o vínculo não vale: ele só é gravado depois do registro
        audit.succeeded(
                Channel.TELEGRAM,
                origin,
                teacher,
                AuthorshipRecord.TEACHER_LINK_TELEGRAM,
                correlationId);
        TelegramLink link = TelegramLink.create(teacher.id(), telegramUserId, chatId, clock.now());
        links.save(link);
        renewal.renew(teacher);
        return new Result(teacher, link);
    }

    /** Sem identificador de correlação do chamador: gera um para o registro de autoria. */
    public Result run(long telegramUserId, long chatId, String code) {
        return run(telegramUserId, chatId, code, GeneratorId.create().toString());
    }

    private DomainException refused(
            String origin, Teacher teacher, ErrorCode code, String correlationId) {
        audit.refused(
                Channel.TELEGRAM,
                origin,
                teacher,
                AuthorshipRecord.TEACHER_LINK_TELEGRAM,
                code,
                correlationId);
        return new DomainException(code);
    }
}
