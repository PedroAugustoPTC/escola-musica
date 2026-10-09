package br.com.felipeblaft.relatorios.dominio.canal;

import java.time.Duration;
import java.time.Instant;
import java.util.function.DoubleSupplier;

/**
 * Política de retentativa da entrega ao orquestrador: espera exponencial a partir do intervalo
 * inicial, limitada pelo teto, com jitter injetável e prazo total (data-model.md §1.2.2).
 */
public final class PoliticaRetentativa {

    /** Jitter de ±10% (contracts/canal-telegram-interno.md §3). */
    private static final double JITTER_MAXIMO = 0.1;

    private final Duration inicial;
    private final Duration teto;
    private final Duration prazo;
    private final DoubleSupplier jitter;

    /**
     * @param jitter devolve um fator em [-1, 1] aplicado como {@code espera × (1 + fator × 0,1)};
     *     zero nos testes determinísticos
     */
    public PoliticaRetentativa(
            Duration inicial, Duration teto, Duration prazo, DoubleSupplier jitter) {
        if (inicial == null || inicial.isZero() || inicial.isNegative()) {
            throw new IllegalArgumentException("intervalo inicial deve ser positivo");
        }
        if (teto == null || teto.compareTo(inicial) < 0) {
            throw new IllegalArgumentException("teto deve ser maior ou igual ao intervalo inicial");
        }
        if (prazo == null || prazo.isZero() || prazo.isNegative()) {
            throw new IllegalArgumentException("prazo deve ser positivo");
        }
        this.inicial = inicial;
        this.teto = teto;
        this.prazo = prazo;
        this.jitter = jitter == null ? () -> 0.0 : jitter;
    }

    public PoliticaRetentativa(Duration inicial, Duration teto, Duration prazo) {
        this(inicial, teto, prazo, () -> 0.0);
    }

    /** Próxima tentativa depois da n-ésima falha (n ≥ 1). */
    public Instant proximaTentativa(int tentativas, Instant agora) {
        if (tentativas < 1) {
            throw new IllegalArgumentException("tentativas deve ser maior ou igual a 1");
        }
        double segundos = inicial.toMillis() / 1000.0;
        double limite = teto.toMillis() / 1000.0;
        for (int i = 1; i < tentativas && segundos < limite; i++) {
            segundos *= 2;
        }
        segundos = Math.min(segundos, limite);
        double fator = Math.max(-1.0, Math.min(1.0, jitter.getAsDouble()));
        long milissegundos = Math.round(segundos * 1000.0 * (1.0 + fator * JITTER_MAXIMO));
        return agora.plusMillis(Math.max(milissegundos, 0));
    }

    /**
     * Próxima tentativa nunca depois do prazo: a falha definitiva é registrada no prazo, não até o
     * teto de espera depois dele.
     */
    public Instant proximaTentativa(int tentativas, Instant agora, Instant prazoEm) {
        Instant proxima = proximaTentativa(tentativas, agora);
        return proxima.isAfter(prazoEm) ? prazoEm : proxima;
    }

    public Instant prazoDe(Instant criadoEm) {
        return criadoEm.plus(prazo);
    }

    public Duration prazo() {
        return prazo;
    }
}
