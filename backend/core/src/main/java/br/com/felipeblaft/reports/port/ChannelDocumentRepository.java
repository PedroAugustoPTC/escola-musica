package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import java.time.Instant;
import java.util.Optional;

/** Identificadores opacos de PDF emitidos para o orquestrador (data-model.md §1.3). */
public interface ChannelDocumentRepository {

    void emit(ChannelDocument document);

    Optional<ChannelDocument> searchByHash(String tokenHash);

    /** Remove os documentos vencidos; devolve quantos. */
    int purgeExpired(Instant now);
}
