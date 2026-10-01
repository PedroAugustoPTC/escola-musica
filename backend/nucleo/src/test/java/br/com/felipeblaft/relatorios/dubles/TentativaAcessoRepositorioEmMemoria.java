package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.acesso.TentativaAcesso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.porta.TentativaAcessoRepositorio;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TentativaAcessoRepositorioEmMemoria implements TentativaAcessoRepositorio {

    private final List<TentativaAcesso> dados = new ArrayList<>();

    @Override
    public Optional<TentativaAcesso> buscar(Canal canal, String origemHash) {
        return dados.stream()
                .filter(t -> t.canal() == canal && t.origemHash().equals(origemHash))
                .findFirst();
    }

    @Override
    public void salvar(TentativaAcesso tentativa) {
        dados.removeIf(t -> t.id().equals(tentativa.id()));
        dados.add(tentativa);
    }

    public List<TentativaAcesso> todas() {
        return List.copyOf(dados);
    }
}
