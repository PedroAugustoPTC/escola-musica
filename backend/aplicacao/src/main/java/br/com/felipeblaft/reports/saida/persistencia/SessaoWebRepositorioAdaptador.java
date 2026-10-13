package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SessaoWebRepositorioAdaptador implements WebSessionRepository {

    private final SessaoWebJpa jpa;

    SessaoWebRepositorioAdaptador(SessaoWebJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<WebSession> searchByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash)
                .map(
                        e ->
                                new WebSession(
                                        e.getId(),
                                        e.getProfessorId(),
                                        e.getTokenHash(),
                                        e.getCriadaEm(),
                                        e.getUltimoUsoEm(),
                                        e.getExpiraEm()));
    }

    @Override
    public void save(WebSession s) {
        jpa.save(
                new SessaoWebEntidade(
                        s.id(),
                        s.teacherId(),
                        s.tokenHash(),
                        s.createdAt(),
                        s.lastUsedAt(),
                        s.expiresAt()));
    }

    @Override
    public void remove(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional
    public int removeExpired(Instant agora) {
        return jpa.removerVencidas(agora);
    }
}
