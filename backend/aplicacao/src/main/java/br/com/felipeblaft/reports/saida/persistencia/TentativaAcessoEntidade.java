package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tentativa_acesso")
public class TentativaAcessoEntidade {

    @Id private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel canal;

    @Column(name = "origem_hash", nullable = false)
    private String origemHash;

    @Column(name = "tentativas_incorretas", nullable = false)
    private int tentativasIncorretas;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected TentativaAcessoEntidade() {}

    TentativaAcessoEntidade(
            UUID id,
            Channel canal,
            String origemHash,
            int tentativasIncorretas,
            Instant bloqueadoAte,
            Instant atualizadoEm) {
        this.id = id;
        this.canal = canal;
        this.origemHash = origemHash;
        this.tentativasIncorretas = tentativasIncorretas;
        this.bloqueadoAte = bloqueadoAte;
        this.atualizadoEm = atualizadoEm;
    }

    UUID getId() {
        return id;
    }

    Channel getCanal() {
        return canal;
    }

    String getOrigemHash() {
        return origemHash;
    }

    int getTentativasIncorretas() {
        return tentativasIncorretas;
    }

    Instant getBloqueadoAte() {
        return bloqueadoAte;
    }

    Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
