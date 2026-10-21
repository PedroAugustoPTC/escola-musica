package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import java.util.Optional;
import java.util.UUID;

public interface TelegramLinkRepository {

    Optional<TelegramLink> searchByTelegramUserId(long telegramUserId);

    Optional<TelegramLink> searchByTeacherId(UUID teacherId);

    /** Grava o vínculo e a conversa; falha se outro update gravou antes (bloqueio otimista). */
    void save(TelegramLink link);

    /**
     * Remove o vínculo; o cadastro de aluno em andamento do vínculo some junto (cascata). Só o
     * {@code TelegramLinkTerminationService} chama este método: todo fim de vínculo passa por ele.
     */
    void remove(UUID id);
}
