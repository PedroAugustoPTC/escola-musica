package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import java.util.Objects;

/** "sair" no Telegram: desfaz o vínculo e todo o estado da conversa (FR-008). */
public class EncerrarVinculoTelegramCasoDeUso {

    private final VinculoTelegramRepositorio vinculos;

    public EncerrarVinculoTelegramCasoDeUso(VinculoTelegramRepositorio vinculos) {
        this.vinculos = Objects.requireNonNull(vinculos);
    }

    public void executar(long telegramUsuarioId) {
        vinculos.buscarPorTelegramUsuarioId(telegramUsuarioId)
                .ifPresent(vinculo -> vinculos.remover(vinculo.id()));
    }
}
