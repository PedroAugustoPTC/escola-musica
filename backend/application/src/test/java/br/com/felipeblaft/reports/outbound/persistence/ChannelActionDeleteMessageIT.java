package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Migração {@code V13} (feature 005, verificação 1 do plano): a {@code CHECK} {@code
 * ck_channel_action_type} passa a aceitar {@code DELETE_MESSAGE}, o repositório grava e lê o tipo
 * novo (com a compensação e a falha {@code MESSAGE_NOT_DELETABLE}) e a restrição continua barrando
 * qualquer outro valor.
 */
class ChannelActionDeleteMessageIT extends IntegrationBase {

    private static final Instant T0 = Instant.parse("2026-10-21T12:00:00Z");
    private static final String PAYLOAD =
            "{\"type\":\"DELETE_MESSAGE\",\"chatId\":4567,\"messageId\":89}";
    private static final String NOTICE =
            "{\"type\":\"MESSAGE\",\"chatId\":4567,\"text\":\"aviso\"}";

    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    @Autowired ChannelActionRepository actions;

    private ChannelAction deletion(String id) {
        return ChannelAction.create(
                id,
                "p:cpf:" + id,
                ChannelActionOrigin.PROACTIVE,
                null,
                4567L,
                UUID.randomUUID(),
                null,
                ChannelActionType.DELETE_MESSAGE,
                PAYLOAD,
                NOTICE,
                T0,
                policy);
    }

    @Test
    void repositorySavesAndReadsBackTheDeletionWithItsCompensation() {
        assertThat(actions.saveBatch(List.of(deletion("p:cpf:1:0")))).isEqualTo(1);

        ChannelAction read = actions.searchById("p:cpf:1:0").orElseThrow();

        assertThat(read.type()).isEqualTo(ChannelActionType.DELETE_MESSAGE);
        assertThat(read.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(read.chatId()).isEqualTo(4567L);
        assertThat(
                        tools.jackson.databind.json.JsonMapper.builder()
                                .build()
                                .readTree(read.payload())
                                .path("messageId")
                                .asLong())
                .isEqualTo(89L);
        assertThat(read.compensation()).contains("aviso");
        assertThat(read.compensatesBy("MESSAGE_NOT_DELETABLE")).isTrue();
    }

    @Test
    void failureMessageNotDeletableIsStoredAndCompensationIsSavedAsMessage() {
        actions.saveBatch(List.of(deletion("p:cpf:2:0")));
        ChannelAction loaded = actions.searchById("p:cpf:2:0").orElseThrow();
        int version = loaded.version();

        loaded.confirmFailure("MESSAGE_NOT_DELETABLE", T0.plusSeconds(5));
        assertThat(actions.updateIfVersion(loaded, version)).isTrue();
        actions.saveBatch(List.of(loaded.createCompensation(T0.plusSeconds(5), policy)));

        ChannelAction failed = actions.searchById("p:cpf:2:0").orElseThrow();
        assertThat(failed.state()).isEqualTo(ChannelActionState.FAILED);
        assertThat(failed.reasonCode()).isEqualTo("MESSAGE_NOT_DELETABLE");
        ChannelAction compensation = actions.searchById("p:cpf:2:0-c").orElseThrow();
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MESSAGE);
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSATION);
        assertThat(compensation.payload()).contains("aviso");
    }

    @Test
    void typeConstraintKeepsItsNameAcceptsAllTypesAndRejectsOthers() {
        assertThat(
                        jdbc.queryForList(
                                "select conname from pg_constraint where conrelid ="
                                        + " 'channel_action'::regclass and contype = 'c'"
                                        + " and conname like '%type%'",
                                String.class))
                .containsExactly("ck_channel_action_type");
        for (ChannelActionType type : ChannelActionType.values()) {
            assertThat(insert("check-" + type.name(), type.name())).isEqualTo(1);
        }

        assertThatThrownBy(() -> insert("check-other", "DELETE_CHAT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_channel_action_type");
    }

    private int insert(String id, String type) {
        return jdbc.update(
                "insert into channel_action (id, batch_id, channel, origin, chat_id, type, state,"
                        + " deadline_at, created_at) values (?, 'b', 'TELEGRAM', 'PROACTIVE', 7,"
                        + " ?, 'PENDING', now() + interval '1 day', now())",
                id,
                type);
    }
}
