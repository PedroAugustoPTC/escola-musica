package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.util.Objects;

/**
 * "sair" no Telegram: desfaz o vínculo e todo o estado da conversa (FR-008), pelo mesmo caminho de
 * todo fim de vínculo: vínculo, cadastro de aluno em andamento e ações pendentes do chat saem
 * juntos.
 */
public class EndTelegramLinkUseCase {

    private final TelegramLinkRepository links;
    private final TelegramLinkTerminationService termination;

    public EndTelegramLinkUseCase(
            TelegramLinkRepository links, TelegramLinkTerminationService termination) {
        this.links = Objects.requireNonNull(links);
        this.termination = Objects.requireNonNull(termination);
    }

    public void run(long telegramUserId) {
        links.searchByTelegramUserId(telegramUserId).ifPresent(termination::terminate);
    }
}
