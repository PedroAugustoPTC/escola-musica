package br.com.felipeblaft.relatorios.dominio.comum;

import java.time.Duration;
import java.util.Objects;

/**
 * Limites e prazos configuráveis das regras de negócio (spec: FR-005, FR-007, FR-008, FR-015,
 * FR-040). O núcleo não lê configuração; a aplicação monta este objeto a partir do ambiente.
 */
public record ParametrosNegocio(
        Duration validadeCodigo,
        int maximoTentativasAcesso,
        Duration duracaoBloqueio,
        Duration inatividadeSessaoWeb,
        Duration expiracaoAudioPendente,
        long limiteAudioBytes) {

    public static final Duration INTERVALO_RENOVACAO_CODIGO = Duration.ofHours(1);
    public static final Duration INTERVALO_RENOVACAO_SESSAO = Duration.ofMinutes(1);

    public ParametrosNegocio {
        Objects.requireNonNull(validadeCodigo);
        Objects.requireNonNull(duracaoBloqueio);
        Objects.requireNonNull(inatividadeSessaoWeb);
        Objects.requireNonNull(expiracaoAudioPendente);
        if (maximoTentativasAcesso < 1 || limiteAudioBytes < 1) {
            throw new IllegalArgumentException("parâmetros de negócio inválidos");
        }
    }

    /** Padrões da spec: 7 dias, 5 tentativas, 15 min, 12 h, 30 min e 50 MB. */
    public static ParametrosNegocio padrao() {
        return new ParametrosNegocio(
                Duration.ofDays(7),
                5,
                Duration.ofMinutes(15),
                Duration.ofHours(12),
                Duration.ofMinutes(30),
                50L * 1024 * 1024);
    }

    public int limiteAudioMb() {
        return (int) (limiteAudioBytes / (1024 * 1024));
    }
}
