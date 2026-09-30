package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AssociacaoRepositorioEmMemoria implements AssociacaoRepositorio {

    private final List<ProfessorAluno> dados = new ArrayList<>();

    @Override
    public boolean existe(UUID professorId, UUID alunoId) {
        return dados.stream()
                .anyMatch(a -> a.professorId().equals(professorId) && a.alunoId().equals(alunoId));
    }

    @Override
    public void salvar(ProfessorAluno associacao) {
        dados.add(associacao);
    }

    public void associar(UUID professorId, UUID alunoId) {
        dados.add(ProfessorAluno.nova(professorId, alunoId, java.time.Instant.EPOCH));
    }

    public List<UUID> alunosDo(UUID professorId) {
        return dados.stream()
                .filter(a -> a.professorId().equals(professorId))
                .map(ProfessorAluno::alunoId)
                .toList();
    }

    public List<ProfessorAluno> todas() {
        return List.copyOf(dados);
    }
}
