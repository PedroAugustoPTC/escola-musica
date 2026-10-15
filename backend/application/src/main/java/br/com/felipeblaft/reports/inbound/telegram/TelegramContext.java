package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.usecase.access.IdentifyTelegramTeacherUseCase.Identification;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados de um único update em processamento: quem falou, em qual chat, o vínculo carregado do banco
 * e as saídas acumuladas (enviadas só depois de gravar o estado).
 */
public final class TelegramContext {

    private final long userId;
    private final long chatId;
    private final Long callbackMessageId;
    private Identification identification;
    private boolean endedLink;
    private final List<TelegramOutput> outputs = new ArrayList<>();

    public TelegramContext(long userId, long chatId, Long callbackMessageId) {
        this.userId = userId;
        this.chatId = chatId;
        this.callbackMessageId = callbackMessageId;
    }

    public long userId() {
        return userId;
    }

    public long chatId() {
        return chatId;
    }

    /** Mensagem que continha o botão tocado (para editar no lugar), se o update é um callback. */
    public Long callbackMessageId() {
        return callbackMessageId;
    }

    public void identify(Identification identification) {
        this.identification = identification;
    }

    public boolean linked() {
        return identification != null && !endedLink;
    }

    public Teacher teacher() {
        return identification.teacher();
    }

    public TelegramLink link() {
        return identification.link();
    }

    public TelegramConversation conversation() {
        return identification.link().conversation();
    }

    public void markEndedLink() {
        this.endedLink = true;
    }

    public boolean endedLink() {
        return endedLink;
    }

    public void answer(TelegramOutput output) {
        outputs.add(output);
    }

    public List<TelegramOutput> outputs() {
        return List.copyOf(outputs);
    }
}
