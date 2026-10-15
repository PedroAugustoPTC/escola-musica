package br.com.felipeblaft.reports.usecase.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryChannelActionUseCaseTest {

    @Test
    void returnsActionStateOrEmptyIfUnknown() {
        InMemoryChannelActionRepository repository = new InMemoryChannelActionRepository();
        new SaveChannelActionsUseCase(
                        repository,
                        new FixedClock(),
                        new RetryPolicy(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofHours(24)))
                .run(
                        "u1",
                        ChannelActionOrigin.UPDATE_REPLY,
                        1L,
                        List.of(
                                new NewAction(
                                        "u1-0",
                                        ChannelActionType.MESSAGE,
                                        1L,
                                        null,
                                        null,
                                        "{}",
                                        null)));
        QueryChannelActionUseCase query = new QueryChannelActionUseCase(repository);

        assertThat(query.run("u1-0").orElseThrow().state()).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(query.run("u9-9")).isEmpty();
    }
}
