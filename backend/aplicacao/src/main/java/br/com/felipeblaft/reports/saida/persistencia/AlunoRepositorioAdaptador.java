package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AlunoRepositorioAdaptador implements StudentRepository {

    private final AlunoJpa jpa;

    AlunoRepositorioAdaptador(AlunoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Student> searchById(UUID id) {
        return jpa.findById(id).map(CadastroMapeador::paraDominio);
    }

    @Override
    public Optional<Student> searchByCpfHash(String cpfHash) {
        return jpa.findByCpfHash(cpfHash).map(CadastroMapeador::paraDominio);
    }

    @Override
    public List<Student> listByTeacher(UUID professorId) {
        return jpa.listarDoProfessor(professorId).stream()
                .map(CadastroMapeador::paraDominio)
                .toList();
    }

    @Override
    public void save(Student aluno) {
        jpa.save(CadastroMapeador.paraEntidade(aluno));
    }
}
