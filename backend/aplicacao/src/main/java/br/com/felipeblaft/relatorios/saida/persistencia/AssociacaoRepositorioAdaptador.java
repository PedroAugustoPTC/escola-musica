package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AssociacaoRepositorioAdaptador implements AssociacaoRepositorio {

    private final ProfessorAlunoJpa jpa;

    AssociacaoRepositorioAdaptador(ProfessorAlunoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existe(UUID professorId, UUID alunoId) {
        return jpa.existsByProfessorIdAndAlunoId(professorId, alunoId);
    }

    @Override
    public void salvar(ProfessorAluno associacao) {
        jpa.save(CadastroMapeador.paraEntidade(associacao));
    }
}
