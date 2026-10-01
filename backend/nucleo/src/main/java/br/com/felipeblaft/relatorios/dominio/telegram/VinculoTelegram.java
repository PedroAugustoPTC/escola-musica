package br.com.felipeblaft.relatorios.dominio.telegram;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo de uma conta do Telegram com um professor (FR-006): só identifica o professor no canal; a
 * autorização continua nos casos de uso (FR-010). Carrega a conversa persistida.
 */
public class VinculoTelegram {

    private final UUID id;
    private final UUID professorId;
    private final long telegramUsuarioId;
    private long chatId;
    private final Instant vinculadoEm;
    private final ConversaTelegram conversa;
    private Instant atualizadoEm;
    private long versao;

    public VinculoTelegram(
            UUID id,
            UUID professorId,
            long telegramUsuarioId,
            long chatId,
            Instant vinculadoEm,
            ConversaTelegram conversa,
            Instant atualizadoEm,
            long versao) {
        this.id = Objects.requireNonNull(id);
        this.professorId = Objects.requireNonNull(professorId);
        this.telegramUsuarioId = telegramUsuarioId;
        this.chatId = chatId;
        this.vinculadoEm = Objects.requireNonNull(vinculadoEm);
        this.conversa = conversa == null ? ConversaTelegram.vazia() : conversa;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
        this.versao = versao;
    }

    public static VinculoTelegram novo(
            UUID professorId, long telegramUsuarioId, long chatId, Instant agora) {
        return new VinculoTelegram(
                GeradorId.novo(),
                professorId,
                telegramUsuarioId,
                chatId,
                agora,
                ConversaTelegram.vazia(),
                agora,
                0L);
    }

    public void atualizarChat(long novoChatId, Instant agora) {
        this.chatId = novoChatId;
        this.atualizadoEm = agora;
    }

    public void tocar(Instant agora) {
        this.atualizadoEm = agora;
    }

    public UUID id() {
        return id;
    }

    public UUID professorId() {
        return professorId;
    }

    public long telegramUsuarioId() {
        return telegramUsuarioId;
    }

    public long chatId() {
        return chatId;
    }

    public Instant vinculadoEm() {
        return vinculadoEm;
    }

    public ConversaTelegram conversa() {
        return conversa;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }

    /** Atualizada pelo repositório após gravar (bloqueio otimista). */
    public void registrarVersaoPersistida(long versaoGravada) {
        this.versao = versaoGravada;
    }

    /** Versão para bloqueio otimista (conversa atualizada por dois updates ao mesmo tempo). */
    public long versao() {
        return versao;
    }
}
