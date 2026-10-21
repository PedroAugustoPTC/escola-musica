package br.com.felipeblaft.reports.architecture.fixture;

import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.UUID;

/** Chama a remoção de vínculo fora do serviço de encerramento (apenas autoteste do ArchUnit). */
public class LinkRemovalFixture {

    private final TelegramLinkRepository links;

    public LinkRemovalFixture(TelegramLinkRepository links) {
        this.links = links;
    }

    public void run(UUID id) {
        links.remove(id);
    }
}
