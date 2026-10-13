package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professor")
public class ProfessorEntidade {

    @Id private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "codigo_acesso_hash")
    private String codigoAcessoHash;

    @Column(name = "codigo_acesso_expira_em")
    private Instant codigoAcessoExpiraEm;

    @Column(name = "codigo_acesso_renovado_em")
    private Instant codigoAcessoRenovadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ProfessorEntidade() {}

    ProfessorEntidade(
            UUID id,
            String nome,
            String email,
            boolean ativo,
            String codigoAcessoHash,
            Instant codigoAcessoExpiraEm,
            Instant codigoAcessoRenovadoEm,
            Instant criadoEm,
            Instant atualizadoEm) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.ativo = ativo;
        this.codigoAcessoHash = codigoAcessoHash;
        this.codigoAcessoExpiraEm = codigoAcessoExpiraEm;
        this.codigoAcessoRenovadoEm = codigoAcessoRenovadoEm;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    UUID getId() {
        return id;
    }

    String getNome() {
        return nome;
    }

    String getEmail() {
        return email;
    }

    boolean isAtivo() {
        return ativo;
    }

    String getCodigoAcessoHash() {
        return codigoAcessoHash;
    }

    Instant getCodigoAcessoExpiraEm() {
        return codigoAcessoExpiraEm;
    }

    Instant getCodigoAcessoRenovadoEm() {
        return codigoAcessoRenovadoEm;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
