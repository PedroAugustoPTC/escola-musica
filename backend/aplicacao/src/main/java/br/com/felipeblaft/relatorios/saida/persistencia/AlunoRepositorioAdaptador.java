package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AlunoRepositorioAdaptador implements AlunoRepositorio {

    private final AlunoJpa jpa;

    AlunoRepositorioAdaptador(AlunoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Aluno> buscarPorId(UUID id) {
        return jpa.findById(id).map(CadastroMapeador::paraDominio);
    }

    @Override
    public Optional<Aluno> buscarPorCpfHash(String cpfHash) {
        return jpa.findByCpfHash(cpfHash).map(CadastroMapeador::paraDominio);
    }

    @Override
    public List<Aluno> listarDoProfessor(UUID professorId) {
        return jpa.listarDoProfessor(professorId).stream().map(CadastroMapeador::paraDominio).toList();
    }

    @Override
    public void salvar(Aluno aluno) {
        jpa.save(CadastroMapeador.paraEntidade(aluno));
    }
}
