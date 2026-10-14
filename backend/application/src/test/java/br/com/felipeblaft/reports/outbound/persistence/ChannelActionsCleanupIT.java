package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.outbound.queue.ChannelActionsCleanupTask;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.usecase.channel.CleanChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * R-11, FR-029: o conteúdo some depois de 24 h, o registro depois de 7 dias, o documento ao vencer.
 */
class ChannelActionsCleanupIT extends IntegrationBase {

    @Autowired SaveChannelActionsUseCase save;
    @Autowired ChannelActionRepository actions;
    @Autowired ChannelDocumentRepository documents;
    @Autowired CleanChannelActionsUseCase clean;
    @Autowired ChannelActionsCleanupTask task;

    private void finalizedAction(String id) {
        save.run(
                "p:" + id,
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                id,
                                ChannelActionType.MESSAGE,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{\"text\":\"conteúdo sigiloso\"}",
                                "{\"text\":\"alternativa sigilosa\"}")));
        ChannelAction action = actions.searchById(id).orElseThrow();
        action.confirmExecuted(clock.now());
        assertThat(actions.updateIfVersion(action, action.version())).isTrue();
    }

    @Test
    void contentClearedAfter24HRecordRemovedAfter7DaysAndExpiredDocumentRemoved() {
        finalizedAction("p:a:0");
        save.run(
                "p:b",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                "p:b:0",
                                ChannelActionType.MESSAGE,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{\"text\":\"ainda pendente\"}",
                                null)));
        documents.emit(
                new ChannelDocument(
                        "a".repeat(64),
                        UUID.randomUUID(),
                        ReportType.LESSON,
                        UUID.randomUUID(),
                        null,
                        "x.pdf",
                        clock.now(),
                        clock.now().plus(Duration.ofHours(25))));

        // antes das 24 h nada muda
        clock.advance(Duration.ofHours(23));
        clean.run();
        assertThat(
                        jdbc.queryForObject(
                                "select payload is not null from channel_action where id = 'p:a:0'",
                                Boolean.class))
                .isTrue();

        clock.advance(Duration.ofHours(2));
        task.clean();

        assertThat(
                        jdbc.queryForObject(
                                "select payload is null and compensation is null from channel_action where id = 'p:a:0'",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select state from channel_action where id = 'p:a:0'",
                                String.class))
                .isEqualTo("EXECUTED");
        // a pendente não é tocada; o documento venceu (25 h)
        assertThat(
                        jdbc.queryForObject(
                                "select payload is not null from channel_action where id = 'p:b:0'",
                                Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from channel_document", Integer.class))
                .isZero();

        clock.advance(Duration.ofDays(7));
        clean.run();

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where id = 'p:a:0'",
                                Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where id = 'p:b:0'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void deliveredNeverConfirmedExpiresAtDeadlineAndContentVanishesAtRetention() {
        String batchId = "u900";
        save.run(
                batchId,
                ChannelActionOrigin.UPDATE_REPLY,
                900L,
                List.of(
                        new NewAction(
                                "u900-e0",
                                ChannelActionType.MESSAGE,
                                1L,
                                null,
                                null,
                                "{\"text\":\"Matteo foi bem\"}",
                                "{\"text\":\"alternativa sigilosa\"}")));

        // dentro do prazo continua entregue e reentregável pelo update repetido
        clock.advance(Duration.ofHours(23));
        task.clean();
        assertThat(
                        jdbc.queryForObject(
                                "select state from channel_action where id = 'u900-e0'",
                                String.class))
                .isEqualTo("DELIVERED");
        assertThat(actions.searchByUpdate(900L)).hasSize(1);

        // vencido o prazo: estado final SEM_CONFIRMACAO, ainda com o conteúdo (retenção de 24 h)
        clock.advance(Duration.ofHours(1));
        task.clean();
        assertThat(
                        jdbc.queryForObject(
                                "select state || '/' || reason_code from channel_action where id = 'u900-e0'",
                                String.class))
                .isEqualTo("PERMANENTLY_FAILED/NO_CONFIRMATION");
        assertThat(
                        jdbc.queryForObject(
                                "select payload is not null from channel_action where id = 'u900-e0'",
                                Boolean.class))
                .isTrue();

        // depois da retenção do conteúdo: payload e compensação zerados
        clock.advance(Duration.ofHours(25));
        task.clean();
        assertThat(
                        jdbc.queryForObject(
                                "select payload is null and compensation is null from channel_action where id = 'u900-e0'",
                                Boolean.class))
                .isTrue();

        // depois da retenção do registro: a linha some
        clock.advance(Duration.ofDays(7));
        task.clean();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where id = 'u900-e0'",
                                Integer.class))
                .isZero();
    }

    @Test
    void withNothingToCleanDoesNotFail() {
        CleanChannelActionsUseCase.Result result = clean.run();
        assertThat(result).isEqualTo(new CleanChannelActionsUseCase.Result(0, 0, 0, 0));
    }
}
