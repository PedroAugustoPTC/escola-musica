package br.com.felipeblaft.reports.port;

import java.util.Objects;

/**
 * Referência a um arquivo guardado no canal (ex.: {@code file_id} do Telegram), sem nenhum byte.
 */
public record ChannelFileReference(String id, String uniqueId, String mime, long size)
        implements AudioInput {

    public ChannelFileReference {
        Objects.requireNonNull(id);
    }
}
