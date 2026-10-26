package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.util.Objects;
import java.util.Optional;

/**
 * Identifica o professor pela conta do Telegram (FR-010): o vínculo só identifica; cada ação é
 * autorizada no seu caso de uso. Professor inativo perde o acesso e o vínculo é encerrado pelo
 * serviço de encerramento. Cada uso renova a validade do código (FR-005).
 */
public class IdentifyTelegramTeacherUseCase {

    public record Identification(Teacher teacher, TelegramLink link) {}

    private final TelegramLinkRepository links;
    private final TeacherRepository teachers;
    private final AccessRenewalService renewal;
    private final TelegramLinkTerminationService termination;

    public IdentifyTelegramTeacherUseCase(
            TelegramLinkRepository links,
            TeacherRepository teachers,
            AccessRenewalService renewal,
            TelegramLinkTerminationService termination) {
        this.links = Objects.requireNonNull(links);
        this.teachers = Objects.requireNonNull(teachers);
        this.renewal = Objects.requireNonNull(renewal);
        this.termination = Objects.requireNonNull(termination);
    }

    public Optional<Identification> run(long telegramUserId) {
        Optional<TelegramLink> link = links.searchByTelegramUserId(telegramUserId);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        Optional<Teacher> teacher =
                teachers.searchById(link.get().teacherId()).filter(Teacher::active);
        if (teacher.isEmpty()) {
            termination.terminate(link.get());
            return Optional.empty();
        }
        renewal.renew(teacher.get());
        return Optional.of(new Identification(teacher.get(), link.get()));
    }
}
