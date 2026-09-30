package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class VinculoTelegramRepositorioAdaptador implements VinculoTelegramRepositorio {

    private final VinculoTelegramJpa jpa;

    VinculoTelegramRepositorioAdaptador(VinculoTelegramJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<VinculoTelegram> buscarPorTelegramUsuarioId(long telegramUsuarioId) {
        return jpa.findByTelegramUsuarioId(telegramUsuarioId).map(VinculoTelegramMapeador::paraDominio);
    }

    @Override
    public Optional<VinculoTelegram> buscarPorProfessorId(UUID professorId) {
        return jpa.findByProfessorId(professorId).map(VinculoTelegramMapeador::paraDominio);
    }

    @Override
    public void salvar(VinculoTelegram vinculo) {
        VinculoTelegramEntidade salvo = jpa.saveAndFlush(VinculoTelegramMapeador.paraEntidade(vinculo));
        vinculo.registrarVersaoPersistida(salvo.bloqueioOtimista);
    }

    @Override
    public void remover(UUID id) {
        jpa.deleteById(id);
        jpa.flush();
    }
}
