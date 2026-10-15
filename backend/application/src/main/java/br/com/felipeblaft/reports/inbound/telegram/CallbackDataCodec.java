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

    public static String encode(String action) {
        return encode(action, "");
    }

    public static String encode(String action, String argument) {
        String data = VERSION + ":" + action + ":" + (argument == null ? "" : argument);
        if (data.getBytes(StandardCharsets.UTF_8).length > LIMIT_BYTES) {
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
