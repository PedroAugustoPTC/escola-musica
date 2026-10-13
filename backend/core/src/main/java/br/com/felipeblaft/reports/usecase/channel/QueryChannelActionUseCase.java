package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import java.util.Objects;
import java.util.Optional;

/**
 * Estado de uma ação, para que o orquestrador, sem guardar estado próprio (Princípio II), saiba se
 * uma ação repetida já foi tratada (FR-013).
 */
public class QueryChannelActionUseCase {

    private final ChannelActionRepository actions;

    public QueryChannelActionUseCase(ChannelActionRepository actions) {
        this.actions = Objects.requireNonNull(actions);
    }

    public Optional<ChannelAction> run(String actionId) {
        return actions.searchById(actionId);
    }
}
