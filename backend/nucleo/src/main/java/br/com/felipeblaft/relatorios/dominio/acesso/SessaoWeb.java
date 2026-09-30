package br.com.felipeblaft.relatorios.dominio.acesso;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão da Web (data-model.md §4): expiração deslizante por inatividade (FR-008), renovada a cada
 * uso com no máximo uma escrita por minuto. No banco fica só o hash do token.
 */
public class SessaoWeb {

    private final UUID id;
    private final UUID professorId;
    private final String tokenHash;
    private final Instant criadaEm;
    private Instant ultimoUsoEm;
    private Instant expiraEm;

    public SessaoWeb(
            UUID id,
            UUID professorId,
            String tokenHash,
            Instant criadaEm,
            Instant ultimoUsoEm,
            Instant expiraEm) {
        this.id = Objects.requireNonNull(id);
        this.professorId = Objects.requireNonNull(professorId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.criadaEm = Objects.requireNonNull(criadaEm);
        this.ultimoUsoEm = Objects.requireNonNull(ultimoUsoEm);
        this.expiraEm = Objects.requireNonNull(expiraEm);
    }

    public static SessaoWeb nova(
            UUID professorId, String tokenHash, Instant agora, Duration inatividade) {
        return new SessaoWeb(
                GeradorId.novo(), professorId, tokenHash, agora, agora, agora.plus(inatividade));
    }

    public boolean validaEm(Instant instante) {
        return instante.isBefore(expiraEm);
    }

    /** Renova a expiração; devolve {@code true} se precisa gravar (1 escrita por minuto, no máximo). */
    public boolean registrarUso(Instant agora, Duration inatividade) {
        if (Duration.between(ultimoUsoEm, agora)
                        .compareTo(ParametrosNegocio.INTERVALO_RENOVACAO_SESSAO)
                < 0) {
            return false;
        }
        ultimoUsoEm = agora;
        expiraEm = agora.plus(inatividade);
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID professorId() {
        return professorId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant criadaEm() {
        return criadaEm;
    }

    public Instant ultimoUsoEm() {
        return ultimoUsoEm;
    }

    public Instant expiraEm() {
        return expiraEm;
    }
}
