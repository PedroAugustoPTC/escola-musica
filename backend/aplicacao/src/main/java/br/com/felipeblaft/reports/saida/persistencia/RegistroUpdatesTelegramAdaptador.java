package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.entrada.telegram.RegistroUpdatesTelegram;
import java.time.Instant;
import java.util.Optional;
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
    public boolean registrarSeNovo(long updateId, Instant agora, String origem) {
        return jpa.registrarSeNovoComOrigem(updateId, agora, origem) > 0;
    }

    @Override
    @Transactional
    public void concluir(long updateId, int totalAcoes) {
        jpa.concluir(updateId, totalAcoes);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SituacaoUpdate> situacao(long updateId) {
        return jpa.findById(updateId)
                .map(
                        e ->
                                new SituacaoUpdate(
                                        e.getTotalAcoes(),
                                        e.getRecebidoEm(),
                                        ORIGEM_INTERNO.equals(e.getOrigem())));
    }

    @Override
    @Transactional
    public int removerAntigos(Instant limite) {
        return jpa.removerAntigos(limite);
    }
}
