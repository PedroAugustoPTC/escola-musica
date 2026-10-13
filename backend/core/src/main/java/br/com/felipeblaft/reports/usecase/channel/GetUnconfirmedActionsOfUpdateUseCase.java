package br.com.felipeblaft.reports.usecase.channel;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import java.util.List;
import java.util.Objects;

/**
 * Update repetido (FR-007): devolve só as ações ainda não confirmadas, na ordem original e com os
 * mesmos ids; lista vazia se todas foram confirmadas.
 */
public class GetUnconfirmedActionsOfUpdateUseCase {

    private final ChannelActionRepository actions;

    public GetUnconfirmedActionsOfUpdateUseCase(ChannelActionRepository actions) {
        this.actions = Objects.requireNonNull(actions);
    }

    public List<ChannelAction> run(long updateId) {
        return actions.searchByUpdate(updateId).stream()
                .filter(action -> action.state() == ChannelActionState.ENTREGUE)
                .filter(action -> action.payload() != null)
                .toList();
    }
}
