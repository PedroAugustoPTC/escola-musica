package br.com.felipeblaft.reports.usecase.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cenários C4 a C6 do contrato: update repetido devolve só o que falta confirmar. */
class GetUnconfirmedActionsOfUpdateUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryChannelActionRepository repository =
            new InMemoryChannelActionRepository();
    private final GetUnconfirmedActionsOfUpdateUseCase get =
            new GetUnconfirmedActionsOfUpdateUseCase(repository);

    private void saveThree() {
        new SaveChannelActionsUseCase(
                        repository,
                        clock,
                        new RetryPolicy(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofHours(24)))
                .run(
                        "u5",
                        ChannelActionOrigin.RESPOSTA_UPDATE,
                        5L,
                        List.of(create("u5-0"), create("u5-1"), create("u5-2")));
    }

    private static NewAction create(String id) {
        return new NewAction(
                id, ChannelActionType.MENSAGEM, 1L, null, null, "{\"texto\":\"x\"}", null);
    }

    @Test
    void nothingConfirmedReturnsEverythingInSameOrderWithSameIds() {
        saveThree();
        assertThat(get.run(5L))
                .extracting(ChannelAction::id)
                .containsExactly("u5-0", "u5-1", "u5-2");
        assertThat(get.run(5L))
                .extracting(ChannelAction::id)
                .containsExactly("u5-0", "u5-1", "u5-2");
    }

    @Test
    void confirmedLeaveTheList() {
        saveThree();
        repository.stored("u5-1").confirmExecuted(clock.now());
        assertThat(get.run(5L)).extracting(ChannelAction::id).containsExactly("u5-0", "u5-2");
    }

    @Test
    void everythingConfirmedReturnsEmptyList() {
        saveThree();
        repository
                .all()
                .forEach(
                        a ->
                                repository
                                        .stored(a.id())
                                        .confirmFailure("ERRO_TELEGRAM", clock.now()));
        assertThat(get.run(5L)).isEmpty();
    }

    @Test
    void updateWithoutActionsOrWithoutContentReturnsEmpty() {
        assertThat(get.run(999L)).isEmpty();
        saveThree();
        repository.stored("u5-0").resetPayload();
        assertThat(get.run(5L)).extracting(ChannelAction::id).containsExactly("u5-1", "u5-2");
    }
}
