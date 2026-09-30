package br.com.felipeblaft.relatorios.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessao_web")
public class SessaoWebEntidade {

    @Id private UUID id;

    @Column(name = "professor_id", nullable = false)
    private UUID professorId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "ultimo_uso_em", nullable = false)
    private Instant ultimoUsoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    protected SessaoWebEntidade() {}

    SessaoWebEntidade(
            UUID id,
            UUID professorId,
            String tokenHash,
            Instant criadaEm,
            Instant ultimoUsoEm,
            Instant expiraEm) {
        this.id = id;
        this.professorId = professorId;
        this.tokenHash = tokenHash;
        this.criadaEm = criadaEm;
        this.ultimoUsoEm = ultimoUsoEm;
        this.expiraEm = expiraEm;
    }

    UUID getId() {
        return id;
    }

    UUID getProfessorId() {
        return professorId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getCriadaEm() {
        return criadaEm;
    }

    Instant getUltimoUsoEm() {
        return ultimoUsoEm;
    }

    Instant getExpiraEm() {
        return expiraEm;
    }
}
