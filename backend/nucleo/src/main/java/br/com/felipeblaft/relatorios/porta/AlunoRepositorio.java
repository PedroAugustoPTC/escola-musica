package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlunoRepositorio {

    Optional<Aluno> buscarPorId(UUID id);

    Optional<Aluno> buscarPorCpfHash(String cpfHash);

    /** Alunos associados ao professor, em ordem alfabética de nome. */
    List<Aluno> listarDoProfessor(UUID professorId);

    void salvar(Aluno aluno);
}
