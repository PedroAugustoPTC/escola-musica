package br.com.felipeblaft.relatorios.dominio.canal;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Referência opaca a um PDF para o orquestrador (R-08): só o hash do token é guardado; a leitura
 * repete a checagem de dono e associação. {@code versao} nulo significa o PDF oficial.
 */
public record DocumentoCanal(
        String tokenHash,
        UUID professorId,
        TipoRelatorio relatorioTipo,
        UUID relatorioId,
        Integer versao,
        String nomeArquivo,
        Instant criadoEm,
        Instant expiraEm) {

    public DocumentoCanal {
        Objects.requireNonNull(tokenHash);
        Objects.requireNonNull(professorId);
        Objects.requireNonNull(relatorioTipo);
        Objects.requireNonNull(relatorioId);
        Objects.requireNonNull(nomeArquivo);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(expiraEm);
        if (versao != null && versao <= 0) {
            throw new IllegalArgumentException("versão deve ser positiva");
        }
    }

    /** SHA-256 em hexadecimal minúsculo (64 caracteres): é o que se guarda no lugar do token. */
    public static String hashDoToken(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponível", erro);
        }
    }

    public boolean oficial() {
        return versao == null;
    }

    public boolean valido(Instant agora) {
        return agora.isBefore(expiraEm);
    }
}
