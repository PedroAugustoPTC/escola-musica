package br.com.felipeblaft.reports.inbound.telegram;

import java.util.List;

/**
 * Resultado do processamento de um update: se já tinha sido processado ({@code repetido}, sem
 * saídas) ou as saídas, na ordem, que o interpretador produziu depois de gravar o estado da
 * conversa.
 */
public record TelegramUpdateResult(long updateId, boolean repeated, List<TelegramOutput> outputs) {

    public static TelegramUpdateResult repeated(long updateId) {
        return new TelegramUpdateResult(updateId, true, List.of());
    }

    public static TelegramUpdateResult create(long updateId, List<TelegramOutput> outputs) {
        return new TelegramUpdateResult(updateId, false, List.copyOf(outputs));
    }
}
