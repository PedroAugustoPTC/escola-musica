package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.Objects;

/**
 * Persiste o estado da conversa depois de cada update (FR-036): o adaptador do Telegram não guarda
 * nada em memória. Dois updates simultâneos da mesma conversa são resolvidos por bloqueio otimista.
 */
public class UpdateTelegramConversationUseCase {

    private final TelegramLinkRepository links;
    private final ClockPort clock;

    public UpdateTelegramConversationUseCase(TelegramLinkRepository links, ClockPort clock) {
        this.links = Objects.requireNonNull(links);
        this.clock = Objects.requireNonNull(clock);
    }

    public void run(TelegramLink link) {
        link.play(clock.now());
        links.save(link);
    }
}
