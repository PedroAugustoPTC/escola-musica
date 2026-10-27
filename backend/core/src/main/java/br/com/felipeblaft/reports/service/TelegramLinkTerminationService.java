package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Único caminho para encerrar um vínculo do Telegram, por qualquer causa ("sair", inativação,
 * reemissão do código, desvinculação): remove o vínculo, descarta o cadastro de aluno em andamento
 * e as ações pendentes do chat.
 */
public class TelegramLinkTerminationService {

    private final TelegramLinkRepository links;
    private final StudentRegistrationDraftRepository drafts;
    private final ChannelActionRepository actions;
    private final ClockPort clock;

    public TelegramLinkTerminationService(
            TelegramLinkRepository links,
            StudentRegistrationDraftRepository drafts,
            ChannelActionRepository actions,
            ClockPort clock) {
        this.links = Objects.requireNonNull(links);
        this.drafts = Objects.requireNonNull(drafts);
        this.actions = Objects.requireNonNull(actions);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Encerra o vínculo dado: remove o vínculo, descarta o cadastro de aluno em andamento (também
     * pela cascata do banco) e as ações {@code PENDING} do chat. Ações já entregues ou finalizadas
     * não mudam.
     */
    public void terminate(TelegramLink link) {
        Objects.requireNonNull(link);
        links.remove(link.id());
        drafts.removeByLinkId(link.id());
        actions.discardPendingByChat(link.chatId(), clock.now());
    }

    /** Informa se o professor tem vínculo do Telegram. */
    public boolean hasLink(UUID teacherId) {
        return links.searchByTeacherId(teacherId).isPresent();
    }

    /** Encerra o vínculo do professor, se houver; devolve {@code true} se havia. */
    public boolean terminateOfTeacher(UUID teacherId) {
        return links.searchByTeacherId(teacherId)
                .map(
                        link -> {
                            terminate(link);
                            return true;
                        })
                .orElse(false);
    }
}
