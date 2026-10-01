package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.acesso.SessaoWeb;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SessaoWebRepositorioEmMemoria implements SessaoWebRepositorio {

    private final Map<UUID, SessaoWeb> dados = new LinkedHashMap<>();
    private int gravacoes;

    @Override
    public Optional<SessaoWeb> buscarPorTokenHash(String tokenHash) {
        return dados.values().stream().filter(s -> s.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public void salvar(SessaoWeb sessao) {
        gravacoes++;
        dados.put(sessao.id(), sessao);
    }

    @Override
    public void remover(UUID id) {
        dados.remove(id);
    }

    @Override
    public int removerVencidas(Instant agora) {
        List<UUID> vencidas =
                dados.values().stream().filter(s -> !s.validaEm(agora)).map(SessaoWeb::id).toList();
        vencidas.forEach(dados::remove);
        return vencidas.size();
    }

    public List<SessaoWeb> todas() {
        return new ArrayList<>(dados.values());
    }

    public int gravacoes() {
        return gravacoes;
    }
}
