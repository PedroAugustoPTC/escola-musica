package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "update_telegram_processado")
public class UpdateTelegramProcessadoEntidade {

    @Id
    @Column(name = "update_id")
    private long updateId;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    @Column(name = "total_acoes")
    private Integer totalAcoes;

    @Column(name = "origem")
    private String origem;

    protected UpdateTelegramProcessadoEntidade() {}

    long getUpdateId() {
        return updateId;
    }

    Instant getRecebidoEm() {
        return recebidoEm;
    }

    Integer getTotalAcoes() {
        return totalAcoes;
    }

    String getOrigem() {
        return origem;
    }
}
