package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AssociacaoRepositorioAdaptador implements AssignmentRepository {

    private final ProfessorAlunoJpa jpa;

    AssociacaoRepositorioAdaptador(ProfessorAlunoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean exists(UUID professorId, UUID alunoId) {
        return jpa.existsByProfessorIdAndAlunoId(professorId, alunoId);
    }

    @Override
    public void save(TeacherStudent associacao) {
        jpa.save(CadastroMapeador.paraEntidade(associacao));
    }
}
