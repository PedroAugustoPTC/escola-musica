package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import java.time.Instant;
import java.util.Optional;

/** Identificadores opacos de PDF emitidos para o orquestrador (data-model.md §1.3). */
public interface DocumentoCanalRepositorio {

    void emitir(DocumentoCanal documento);

    Optional<DocumentoCanal> buscarPorHash(String tokenHash);

    /** Remove os documentos vencidos; devolve quantos. */
    int expurgarVencidos(Instant agora);
}
