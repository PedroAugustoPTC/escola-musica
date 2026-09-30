package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.acesso.TentativaAcesso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.porta.TentativaAcessoRepositorio;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tentativas de acesso gravadas numa transação independente ({@code REQUIRES_NEW}): a falha de
 * código fica registrada mesmo quando o caso de uso que a detectou é desfeito (FR-007, lição 4).
 */
@Repository
class TentativaAcessoRepositorioAdaptador implements TentativaAcessoRepositorio {

    private final TentativaAcessoJpa jpa;

    TentativaAcessoRepositorioAdaptador(TentativaAcessoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<TentativaAcesso> buscar(Canal canal, String origemHash) {
        return jpa.findByCanalAndOrigemHash(canal, origemHash)
                .map(
                        e ->
                                new TentativaAcesso(
                                        e.getId(),
                                        e.getCanal(),
                                        e.getOrigemHash(),
                                        e.getTentativasIncorretas(),
                                        e.getBloqueadoAte(),
                                        e.getAtualizadoEm()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvar(TentativaAcesso t) {
        jpa.save(
                new TentativaAcessoEntidade(
                        t.id(),
                        t.canal(),
                        t.origemHash(),
                        t.tentativasIncorretas(),
                        t.bloqueadoAte(),
                        t.atualizadoEm()));
    }
}
