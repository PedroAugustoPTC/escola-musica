package br.com.felipeblaft.reports.inbound.telegram;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * {@code callback_data} no formato {@code v1:<acao>:<argumento>}, com no máximo 64 bytes
 * (contracts/telegram-conversa.md §3). O argumento nunca identifica o professor e é sempre
 * revalidado contra o estado persistido.
 */
public final class CallbackDataCodec {

    public static final String VERSION = "v1";
    public static final int LIMIT_BYTES = 64;

    public record CallbackData(String action, String argument) {}

    private CallbackDataCodec() {}

    /** Bytes que sobram para o argumento depois de `v1:<acao>:` dentro do limite de 64 bytes. */
    public static int maxArgumentBytes(String action) {
        return LIMIT_BYTES - bytes(VERSION + ":" + action + ":");
    }

    /** O argumento cabe em `callback_data` sem estourar o limite do Telegram? */
    public static boolean fits(String action, String argument) {
        return argument == null || bytes(argument) <= maxArgumentBytes(action);
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    public static String encode(String action) {
        return encode(action, "");
    }

    public static String encode(String action, String argument) {
        String data = VERSION + ":" + action + ":" + (argument == null ? "" : argument);
        if (bytes(data) > LIMIT_BYTES) {
            throw new IllegalArgumentException("callback_data acima de 64 bytes");
        }
        return data;
    }

    public static Optional<CallbackData> decode(String data) {
        if (data == null) {
            return Optional.empty();
        }
        String[] parts = data.split(":", 3);
        if (parts.length < 2 || !VERSION.equals(parts[0]) || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CallbackData(parts[1], parts.length == 3 ? parts[2] : ""));
    }
}
