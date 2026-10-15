package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.Objects;

/** "sair" no Telegram: desfaz o vínculo e todo o estado da conversa (FR-008). */
public class EndTelegramLinkUseCase {

    private final TelegramLinkRepository links;

    public EndTelegramLinkUseCase(TelegramLinkRepository links) {
        this.links = Objects.requireNonNull(links);
    }

    public void run(long telegramUserId) {
        links.searchByTelegramUserId(telegramUserId).ifPresent(link -> links.remove(link.id()));
    }
}
