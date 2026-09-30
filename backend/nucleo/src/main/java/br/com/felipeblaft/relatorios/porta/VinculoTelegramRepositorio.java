package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import java.util.Optional;
import java.util.UUID;

public interface VinculoTelegramRepositorio {

    Optional<VinculoTelegram> buscarPorTelegramUsuarioId(long telegramUsuarioId);

    Optional<VinculoTelegram> buscarPorProfessorId(UUID professorId);

    /** Grava o vínculo e a conversa; falha se outro update gravou antes (bloqueio otimista). */
    void salvar(VinculoTelegram vinculo);

    void remover(UUID id);
}
