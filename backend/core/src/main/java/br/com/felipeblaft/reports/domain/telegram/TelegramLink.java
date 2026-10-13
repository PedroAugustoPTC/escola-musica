package br.com.felipeblaft.reports.domain.telegram;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo de uma conta do Telegram com um professor (FR-006): só identifica o professor no canal; a
 * autorização continua nos casos de uso (FR-010). Carrega a conversa persistida.
 */
public class TelegramLink {

    private final UUID id;
    private final UUID teacherId;
    private final long telegramUserId;
    private long chatId;
    private final Instant linkedAt;
    private final TelegramConversation conversation;
    private Instant updatedAt;
    private long version;

    public TelegramLink(
            UUID id,
            UUID teacherId,
            long telegramUserId,
            long chatId,
            Instant linkedAt,
            TelegramConversation conversation,
            Instant updatedAt,
            long version) {
        this.id = Objects.requireNonNull(id);
        this.teacherId = Objects.requireNonNull(teacherId);
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.linkedAt = Objects.requireNonNull(linkedAt);
        this.conversation = conversation == null ? TelegramConversation.empty() : conversation;
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static TelegramLink create(
            UUID teacherId, long telegramUserId, long chatId, Instant now) {
        return new TelegramLink(
                GeneratorId.create(),
                teacherId,
                telegramUserId,
                chatId,
                now,
                TelegramConversation.empty(),
                now,
                0L);
    }

    public void updateChat(long newChatId, Instant now) {
        this.chatId = newChatId;
        this.updatedAt = now;
    }

    public void play(Instant now) {
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID teacherId() {
        return teacherId;
    }

    public long telegramUserId() {
        return telegramUserId;
    }

    public long chatId() {
        return chatId;
    }

    public Instant linkedAt() {
        return linkedAt;
    }

    public TelegramConversation conversation() {
        return conversation;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Atualizada pelo repositório após gravar (bloqueio otimista). */
    public void registerPersistedVersion(long savedVersion) {
        this.version = savedVersion;
    }

    /** Versão para bloqueio otimista (conversa atualizada por dois updates ao mesmo tempo). */
    public long version() {
        return version;
    }
}
