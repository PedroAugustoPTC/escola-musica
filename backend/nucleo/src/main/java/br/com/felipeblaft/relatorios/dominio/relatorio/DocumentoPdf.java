package br.com.felipeblaft.relatorios.dominio.relatorio;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** PDF de uma versão (data-model.md §12). A chave de armazenamento nunca é exposta (R-11). */
public record DocumentoPdf(
        UUID id,
        UUID versaoId,
        TipoDocumento tipo,
        String chaveArmazenamento,
        long tamanhoBytes,
        String sha256,
        Instant geradoEm) {

    public DocumentoPdf {
        Objects.requireNonNull(id);
        Objects.requireNonNull(versaoId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(chaveArmazenamento);
        Objects.requireNonNull(sha256);
        Objects.requireNonNull(geradoEm);
    }

    public static DocumentoPdf novo(
            UUID versaoId,
            TipoDocumento tipo,
            String chave,
            long tamanho,
            String sha256,
            Instant agora) {
        return new DocumentoPdf(GeradorId.novo(), versaoId, tipo, chave, tamanho, sha256, agora);
    }
}
