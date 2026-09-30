package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class ProfessorRepositorioAdaptador implements ProfessorRepositorio {

    private final ProfessorJpa jpa;

    ProfessorRepositorioAdaptador(ProfessorJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Professor> buscarPorId(UUID id) {
        return jpa.findById(id).map(CadastroMapeador::paraDominio);
    }

    @Override
    public Optional<Professor> buscarPorEmail(String emailNormalizado) {
        return jpa.findByEmail(emailNormalizado).map(CadastroMapeador::paraDominio);
    }

    @Override
    public Optional<Professor> buscarPorCodigoAcessoHash(String hash) {
        return jpa.findByCodigoAcessoHash(hash).map(CadastroMapeador::paraDominio);
    }

    @Override
    public void salvar(Professor professor) {
        jpa.save(CadastroMapeador.paraEntidade(professor));
    }
}
