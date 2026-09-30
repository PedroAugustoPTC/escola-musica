package br.com.felipeblaft.relatorios.saida.persistencia;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UpdateTelegramProcessadoJpa extends JpaRepository<UpdateTelegramProcessadoEntidade, Long> {

    /** Insere o {@code update_id}; devolve 0 se ele já foi processado. */
    @Modifying
    @Query(
            value =
                    "insert into update_telegram_processado (update_id, recebido_em) values (:updateId, :agora)"
                            + " on conflict (update_id) do nothing",
            nativeQuery = true)
    int registrarSeNovo(long updateId, Instant agora);

    @Modifying
    @Query("delete from UpdateTelegramProcessadoEntidade u where u.recebidoEm < :limite")
    int removerAntigos(Instant limite);
}
