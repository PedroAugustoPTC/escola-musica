package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class VinculoTelegramRepositorioAdaptador implements TelegramLinkRepository {

    private final VinculoTelegramJpa jpa;

    VinculoTelegramRepositorioAdaptador(VinculoTelegramJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<TelegramLink> searchByTelegramUserId(long telegramUsuarioId) {
        return jpa.findByTelegramUsuarioId(telegramUsuarioId)
                .map(VinculoTelegramMapeador::paraDominio);
    }

    @Override
    public Optional<TelegramLink> searchByTeacherId(UUID professorId) {
        return jpa.findByProfessorId(professorId).map(VinculoTelegramMapeador::paraDominio);
    }

    @Override
    public void save(TelegramLink vinculo) {
        VinculoTelegramEntidade salvo =
                jpa.saveAndFlush(VinculoTelegramMapeador.paraEntidade(vinculo));
        vinculo.registerPersistedVersion(salvo.bloqueioOtimista);
    }

    @Override
    public void remove(UUID id) {
        jpa.deleteById(id);
        jpa.flush();
    }
}
