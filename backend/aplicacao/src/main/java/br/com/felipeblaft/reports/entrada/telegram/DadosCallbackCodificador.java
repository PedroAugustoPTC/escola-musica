package br.com.felipeblaft.reports.entrada.telegram;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * {@code callback_data} no formato {@code v1:<acao>:<argumento>}, com no máximo 64 bytes
 * (contracts/telegram-conversa.md §3). O argumento nunca identifica o professor e é sempre
 * revalidado contra o estado persistido.
 */
public final class DadosCallbackCodificador {

    public static final String VERSAO = "v1";
    public static final int LIMITE_BYTES = 64;

    public record DadosCallback(String acao, String argumento) {}

    private DadosCallbackCodificador() {}

    public static String codificar(String acao) {
        return codificar(acao, "");
    }

    public static String codificar(String acao, String argumento) {
        String dados = VERSAO + ":" + acao + ":" + (argumento == null ? "" : argumento);
        if (dados.getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES) {
            throw new IllegalArgumentException("callback_data acima de 64 bytes");
        }
        return dados;
    }

    public static Optional<DadosCallback> decodificar(String dados) {
        if (dados == null) {
            return Optional.empty();
        }
        String[] partes = dados.split(":", 3);
        if (partes.length < 2 || !VERSAO.equals(partes[0]) || partes[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DadosCallback(partes[1], partes.length == 3 ? partes[2] : ""));
    }
}
