package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;
import java.util.UUID;

public interface AssociacaoRepositorio {

    boolean existe(UUID professorId, UUID alunoId);

    void salvar(ProfessorAluno associacao);
}
