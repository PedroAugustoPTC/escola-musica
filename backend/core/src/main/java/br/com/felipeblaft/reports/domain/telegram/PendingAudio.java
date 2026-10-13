package br.com.felipeblaft.reports.domain.telegram;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Áudio recebido no Telegram aguardando a confirmação do aluno (FR-040). Guarda só a referência ao
 * arquivo no Telegram: nenhum byte de áudio fica no sistema antes da confirmação (R-05).
 */
public record PendingAudio(
        String fileId, String fileUniqueId, String mime, long size, Instant receivedAt) {

    public PendingAudio {
        Objects.requireNonNull(fileId);
        Objects.requireNonNull(receivedAt);
    }

    public boolean expiredAt(Instant now, Duration expiry) {
        return !now.isBefore(receivedAt.plus(expiry));
    }
}
