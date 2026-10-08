package br.com.felipeblaft.relatorios.entrada.telegram;

import java.util.List;

/**
 * Resultado do processamento de um update: se já tinha sido processado ({@code repetido}, sem
 * saídas) ou as saídas, na ordem, que o interpretador produziu depois de gravar o estado da
 * conversa.
 */
public record ResultadoUpdateTelegram(long updateId, boolean repetido, List<SaidaTelegram> saidas) {

    public static ResultadoUpdateTelegram repetido(long updateId) {
        return new ResultadoUpdateTelegram(updateId, true, List.of());
    }

    public static ResultadoUpdateTelegram novo(long updateId, List<SaidaTelegram> saidas) {
        return new ResultadoUpdateTelegram(updateId, false, List.copyOf(saidas));
    }
}
