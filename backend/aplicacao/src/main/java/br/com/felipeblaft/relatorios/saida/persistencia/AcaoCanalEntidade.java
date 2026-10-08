package br.com.felipeblaft.relatorios.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Linha da outbox (data-model.md §1.2). A entidade só serve à leitura: as gravações são SQL nativo
 * condicional ({@link AcaoCanalJpa}), porque a concorrência é controlada pela coluna {@code versao}
 * e não pelo contexto de persistência.
 */
@Entity
@Table(name = "acao_canal")
public class AcaoCanalEntidade {

    @Id String id;

    @Column(name = "seq", insertable = false, updatable = false)
    long seq;

    @Column(name = "lote_id", nullable = false)
    String loteId;

    @Column(nullable = false)
    String canal;

    @Column(nullable = false)
    String origem;

    @Column(name = "update_id")
    Long updateId;

    @Column(name = "chat_id", nullable = false)
    long chatId;

    @Column(name = "professor_id")
    UUID professorId;

    @Column(name = "trabalho_id")
    UUID trabalhoId;

    @Column(nullable = false)
    String tipo;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String payload;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String compensacao;

    @Column(nullable = false)
    String estado;

    @Column(nullable = false)
    int tentativas;

    @Column(name = "proxima_tentativa_em")
    Instant proximaTentativaEm;

    @Column(name = "prazo_em", nullable = false)
    Instant prazoEm;

    @Column(nullable = false)
    int versao;

    @Column(name = "criado_em", nullable = false)
    Instant criadoEm;

    @Column(name = "entregue_em")
    Instant entregueEm;

    @Column(name = "finalizado_em")
    Instant finalizadoEm;

    @Column(name = "codigo_motivo")
    String codigoMotivo;

    protected AcaoCanalEntidade() {}
}
