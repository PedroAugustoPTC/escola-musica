package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.acesso.SessaoWeb;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SessaoWebRepositorioAdaptador implements SessaoWebRepositorio {

    private final SessaoWebJpa jpa;

    SessaoWebRepositorioAdaptador(SessaoWebJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<SessaoWeb> buscarPorTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash)
                .map(
                        e ->
                                new SessaoWeb(
                                        e.getId(),
                                        e.getProfessorId(),
                                        e.getTokenHash(),
                                        e.getCriadaEm(),
                                        e.getUltimoUsoEm(),
                                        e.getExpiraEm()));
    }

    @Override
    public void salvar(SessaoWeb s) {
        jpa.save(
                new SessaoWebEntidade(
                        s.id(),
                        s.professorId(),
                        s.tokenHash(),
                        s.criadaEm(),
                        s.ultimoUsoEm(),
                        s.expiraEm()));
    }

    @Override
    public void remover(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional
    public int removerVencidas(Instant agora) {
        return jpa.removerVencidas(agora);
    }
}
