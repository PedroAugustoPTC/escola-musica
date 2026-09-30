package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AlunoRepositorioEmMemoria implements AlunoRepositorio {

    private final Map<UUID, Aluno> dados = new LinkedHashMap<>();
    private final AssociacaoRepositorioEmMemoria associacoes;

    public AlunoRepositorioEmMemoria(AssociacaoRepositorioEmMemoria associacoes) {
        this.associacoes = associacoes;
    }

    @Override
    public Optional<Aluno> buscarPorId(UUID id) {
        return Optional.ofNullable(dados.get(id));
    }

    @Override
    public Optional<Aluno> buscarPorCpfHash(String cpfHash) {
        return dados.values().stream().filter(a -> a.cpfHash().equals(cpfHash)).findFirst();
    }

    @Override
    public List<Aluno> listarDoProfessor(UUID professorId) {
        return associacoes.alunosDo(professorId).stream()
                .map(dados::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Aluno::nome))
                .toList();
    }

    @Override
    public void salvar(Aluno aluno) {
        dados.put(aluno.id(), aluno);
    }

    public List<Aluno> todos() {
        return new ArrayList<>(dados.values());
    }
}
