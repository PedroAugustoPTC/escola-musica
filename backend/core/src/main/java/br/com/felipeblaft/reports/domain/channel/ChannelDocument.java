package br.com.felipeblaft.reports.domain.channel;

import br.com.felipeblaft.reports.domain.common.ReportType;
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
public record ChannelDocument(
        String tokenHash,
        UUID teacherId,
        ReportType reportType,
        UUID reportId,
        Integer version,
        String fileName,
        Instant createdAt,
        Instant expiresAt) {

    public ChannelDocument {
        Objects.requireNonNull(tokenHash);
        Objects.requireNonNull(teacherId);
        Objects.requireNonNull(reportType);
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        if (version != null && version <= 0) {
            throw new IllegalArgumentException("versão deve ser positiva");
        }
    }

    /** SHA-256 em hexadecimal minúsculo (64 caracteres): é o que se guarda no lugar do token. */
    public static String hashDoToken(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponível", error);
        }
    }

    public boolean official() {
        return version == null;
    }

    public boolean valid(Instant now) {
        return now.isBefore(expiresAt);
    }
}
