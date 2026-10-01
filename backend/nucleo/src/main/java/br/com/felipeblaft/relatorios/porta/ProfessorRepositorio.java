package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import java.util.Optional;
import java.util.UUID;

public interface ProfessorRepositorio {

    Optional<Professor> buscarPorId(UUID id);

    Optional<Professor> buscarPorEmail(String emailNormalizado);

    Optional<Professor> buscarPorCodigoAcessoHash(String hash);

    void salvar(Professor professor);
}
