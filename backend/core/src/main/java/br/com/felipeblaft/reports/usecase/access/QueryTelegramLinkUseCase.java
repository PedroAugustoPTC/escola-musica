package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.Objects;
import java.util.UUID;

/** Informa se o professor tem conta do Telegram vinculada (usado pela sessão da Web). */
public class QueryTelegramLinkUseCase {

    private final TelegramLinkRepository links;

    public QueryTelegramLinkUseCase(TelegramLinkRepository links) {
        this.links = Objects.requireNonNull(links);
    }

    public boolean run(UUID teacherId) {
        return links.searchByTeacherId(teacherId).isPresent();
    }
}
