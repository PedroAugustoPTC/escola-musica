package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VinculoTelegramRepositorioEmMemoria implements VinculoTelegramRepositorio {

    private final Map<UUID, VinculoTelegram> dados = new LinkedHashMap<>();

    @Override
    public Optional<VinculoTelegram> buscarPorTelegramUsuarioId(long telegramUsuarioId) {
        return dados.values().stream()
                .filter(v -> v.telegramUsuarioId() == telegramUsuarioId)
                .findFirst();
    }

    @Override
    public Optional<VinculoTelegram> buscarPorProfessorId(UUID professorId) {
        return dados.values().stream().filter(v -> v.professorId().equals(professorId)).findFirst();
    }

    @Override
    public void salvar(VinculoTelegram vinculo) {
        dados.put(vinculo.id(), vinculo);
    }

    @Override
    public void remover(UUID id) {
        dados.remove(id);
    }

    public List<VinculoTelegram> todos() {
        return new ArrayList<>(dados.values());
    }
}
