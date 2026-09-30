package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ProfessorRepositorioEmMemoria implements ProfessorRepositorio {

    private final Map<UUID, Professor> dados = new LinkedHashMap<>();
    private int gravacoes;

    @Override
    public Optional<Professor> buscarPorId(UUID id) {
        return Optional.ofNullable(dados.get(id));
    }

    @Override
    public Optional<Professor> buscarPorEmail(String emailNormalizado) {
        return dados.values().stream().filter(p -> p.email().equals(emailNormalizado)).findFirst();
    }

    @Override
    public Optional<Professor> buscarPorCodigoAcessoHash(String hash) {
        return dados.values().stream()
                .filter(p -> Objects.equals(p.codigoAcessoHash(), hash))
                .findFirst();
    }

    @Override
    public void salvar(Professor professor) {
        gravacoes++;
        dados.put(professor.id(), professor);
    }

    public List<Professor> todos() {
        return new ArrayList<>(dados.values());
    }

    public int gravacoes() {
        return gravacoes;
    }
}
