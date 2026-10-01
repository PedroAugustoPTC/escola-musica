package br.com.felipeblaft.relatorios.porta;

import java.util.Objects;

/**
 * Referência a um arquivo guardado no canal (ex.: {@code file_id} do Telegram), sem nenhum byte.
 */
public record ReferenciaArquivoCanal(String id, String idUnico, String mime, long tamanho)
        implements EntradaAudio {

    public ReferenciaArquivoCanal {
        Objects.requireNonNull(id);
    }
}
