package br.com.felipeblaft.relatorios.dominio.acesso;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tentativas incorretas de código por origem (data-model.md §6, FR-007): após o máximo de falhas
 * consecutivas, bloqueia por um período; um sucesso ou o fim do bloqueio zeram a contagem.
 */
public class TentativaAcesso {

    private final UUID id;
    private final Canal canal;
    private final String origemHash;
    private int tentativasIncorretas;
    private Instant bloqueadoAte;
    private Instant atualizadoEm;

    public TentativaAcesso(
            UUID id,
            Canal canal,
            String origemHash,
            int tentativasIncorretas,
            Instant bloqueadoAte,
            Instant atualizadoEm) {
        this.id = Objects.requireNonNull(id);
        this.canal = Objects.requireNonNull(canal);
        this.origemHash = Objects.requireNonNull(origemHash);
        this.tentativasIncorretas = tentativasIncorretas;
        this.bloqueadoAte = bloqueadoAte;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
    }

    public static TentativaAcesso nova(Canal canal, String origemHash, Instant agora) {
        return new TentativaAcesso(GeradorId.novo(), canal, origemHash, 0, null, agora);
    }

    /** Tempo restante de bloqueio, se houver. */
    public Optional<Duration> bloqueioRestante(Instant agora) {
        if (bloqueadoAte != null && agora.isBefore(bloqueadoAte)) {
            return Optional.of(Duration.between(agora, bloqueadoAte));
        }
        return Optional.empty();
    }

    /** Bloqueio que já terminou: a contagem deve ser zerada (lição 5 do MVP). */
    public boolean bloqueioEncerrado(Instant agora) {
        return bloqueadoAte != null && !agora.isBefore(bloqueadoAte);
    }

    public void registrarFalha(Instant agora, int maximoTentativas, Duration duracaoBloqueio) {
        if (bloqueioEncerrado(agora)) {
            zerar(agora);
        }
        tentativasIncorretas++;
        if (tentativasIncorretas >= maximoTentativas) {
            bloqueadoAte = agora.plus(duracaoBloqueio);
        }
        atualizadoEm = agora;
    }

    public void zerar(Instant agora) {
        tentativasIncorretas = 0;
        bloqueadoAte = null;
        atualizadoEm = agora;
    }

    public UUID id() {
        return id;
    }

    public Canal canal() {
        return canal;
    }

    public String origemHash() {
        return origemHash;
    }

    public int tentativasIncorretas() {
        return tentativasIncorretas;
    }

    public Instant bloqueadoAte() {
        return bloqueadoAte;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }
}
