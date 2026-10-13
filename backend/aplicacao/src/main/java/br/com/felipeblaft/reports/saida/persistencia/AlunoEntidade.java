package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "aluno")
public class AlunoEntidade {

    @Id private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "cpf_cifrado", nullable = false)
    private byte[] cpfCifrado;

    @Column(name = "cpf_hash", nullable = false)
    private String cpfHash;

    @Column(name = "nome_responsavel")
    private String nomeResponsavel;

    private String instrumento;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected AlunoEntidade() {}

    AlunoEntidade(
            UUID id,
            String nome,
            LocalDate dataNascimento,
            byte[] cpfCifrado,
            String cpfHash,
            String nomeResponsavel,
            String instrumento,
            boolean ativo,
            Instant criadoEm,
            Instant atualizadoEm) {
        this.id = id;
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.cpfCifrado = cpfCifrado;
        this.cpfHash = cpfHash;
        this.nomeResponsavel = nomeResponsavel;
        this.instrumento = instrumento;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    UUID getId() {
        return id;
    }

    String getNome() {
        return nome;
    }

    LocalDate getDataNascimento() {
        return dataNascimento;
    }

    byte[] getCpfCifrado() {
        return cpfCifrado;
    }

    String getCpfHash() {
        return cpfHash;
    }

    String getNomeResponsavel() {
        return nomeResponsavel;
    }

    String getInstrumento() {
        return instrumento;
    }

    boolean isAtivo() {
        return ativo;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
