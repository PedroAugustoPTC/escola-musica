package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.entrada.telegram.RegistroUpdatesTelegram;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class RegistroUpdatesTelegramAdaptador implements RegistroUpdatesTelegram {

    private final UpdateTelegramProcessadoJpa jpa;

    RegistroUpdatesTelegramAdaptador(UpdateTelegramProcessadoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public boolean registrarSeNovo(long updateId, Instant agora) {
        return jpa.registrarSeNovo(updateId, agora) > 0;
    }

    @Override
    @Transactional
    public int removerAntigos(Instant limite) {
        return jpa.removerAntigos(limite);
    }
}
