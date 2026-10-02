package br.com.felipeblaft.relatorios.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Identificador opaco de PDF para o orquestrador (data-model.md §1.3). */
@Entity
@Table(name = "documento_canal")
public class DocumentoCanalEntidade {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, columnDefinition = "char(64)")
    String tokenHash;

    @Column(name = "professor_id", nullable = false)
    UUID professorId;

    @Column(name = "relatorio_tipo", nullable = false)
    String relatorioTipo;

    @Column(name = "relatorio_id", nullable = false)
    UUID relatorioId;

    Integer versao;

    @Column(name = "nome_arquivo", nullable = false)
    String nomeArquivo;

    @Column(name = "criado_em", nullable = false)
    Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    Instant expiraEm;

    protected DocumentoCanalEntidade() {}
}
