package br.com.felipeblaft.reports.usecase.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaveChannelActionsUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryChannelActionRepository repository =
            new InMemoryChannelActionRepository();
    private final SaveChannelActionsUseCase save =
            new SaveChannelActionsUseCase(
                    repository,
                    clock,
                    new RetryPolicy(
                            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)));

    private static NewAction create(String id, UUID teacher) {
        return new NewAction(
                id, ChannelActionType.MENSAGEM, 77L, teacher, null, "{\"texto\":\"a\"}", null);
    }

    @Test
    void replyToUpdateSavesDeliveredInReceivedOrder() {
        int saved =
                save.run(
                        "u9",
                        ChannelActionOrigin.RESPOSTA_UPDATE,
                        9L,
                        List.of(create("u9-0", null), create("u9-1", null)));
        assertThat(saved).isEqualTo(2);
        List<ChannelAction> actions = repository.searchByUpdate(9L);
        assertThat(actions).extracting(ChannelAction::id).containsExactly("u9-0", "u9-1");
        assertThat(actions)
                .allSatisfy(
                        action -> {
                            assertThat(action.state()).isEqualTo(ChannelActionState.ENTREGUE);
                            assertThat(action.batchId()).isEqualTo("u9");
                            assertThat(action.deadlineAt())
                                    .isEqualTo(clock.now().plus(Duration.ofHours(24)));
                        });
    }

    @Test
    void proactiveSavesPendingDueNow() {
        UUID teacher = UUID.randomUUID();
        save.run(
                "p:resultado:t",
                ChannelActionOrigin.PROATIVA,
                null,
                List.of(create("p:resultado:t:0", teacher)));
        ChannelAction action = repository.searchById("p:resultado:t:0").orElseThrow();
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDENTE);
        assertThat(action.nextAttemptAt()).isEqualTo(clock.now());
        assertThat(action.teacherId()).isEqualTo(teacher);
    }

    @Test
    void sameIdDoesNotOverwriteNorDuplicate() {
        save.run("u9", ChannelActionOrigin.RESPOSTA_UPDATE, 9L, List.of(create("u9-0", null)));
        repository.stored("u9-0").confirmExecuted(clock.now());
        int second =
                save.run(
                        "u9",
                        ChannelActionOrigin.RESPOSTA_UPDATE,
                        9L,
                        List.of(create("u9-0", null)));
        assertThat(second).isZero();
        assertThat(repository.all()).hasSize(1);
        assertThat(repository.stored("u9-0").state()).isEqualTo(ChannelActionState.EXECUTADA);
    }
}
