package br.com.felipeblaft.relatorios.dominio.telegram;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Áudio recebido no Telegram aguardando a confirmação do aluno (FR-040). Guarda só a referência ao
 * arquivo no Telegram: nenhum byte de áudio fica no sistema antes da confirmação (R-05).
 */
public record AudioPendente(
        String fileId, String fileUniqueId, String mime, long tamanho, Instant recebidoEm) {

    public AudioPendente {
        Objects.requireNonNull(fileId);
        Objects.requireNonNull(recebidoEm);
    }

    public boolean expiradoEm(Instant agora, Duration expiracao) {
        return !agora.isBefore(recebidoEm.plus(expiracao));
    }
}
