package br.com.felipeblaft.relatorios.dominio.cadastro;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;

/**
 * Aluno (data-model.md §2). O CPF só existe cifrado e como hash; nunca é lido de volta para
 * exibição nem enviado à IA (FR-011, FR-023).
 */
public class Aluno {

    public static final int MAIORIDADE = 18;

    private final UUID id;
    private String nome;
    private LocalDate dataNascimento;
    private final byte[] cpfCifrado;
    private final String cpfHash;
    private String nomeResponsavel;
    private String instrumento;
    private final boolean ativo;
    private final Instant criadoEm;
    private Instant atualizadoEm;

    public Aluno(
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
        this.id = Objects.requireNonNull(id);
        this.nome = Objects.requireNonNull(nome);
        this.dataNascimento = Objects.requireNonNull(dataNascimento);
        this.cpfCifrado = Objects.requireNonNull(cpfCifrado).clone();
        this.cpfHash = Objects.requireNonNull(cpfHash);
        this.nomeResponsavel = nomeResponsavel;
        this.instrumento = instrumento;
        this.ativo = ativo;
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
    }

    public static Aluno novo(
            String nome,
            LocalDate dataNascimento,
            byte[] cpfCifrado,
            String cpfHash,
            String nomeResponsavel,
            String instrumento,
            Instant agora) {
        return new Aluno(
                GeradorId.novo(),
                nome,
                dataNascimento,
                cpfCifrado,
                cpfHash,
                nomeResponsavel,
                instrumento,
                true,
                agora,
                agora);
    }

    /** Idade completa em anos na data informada. */
    public int idadeEm(LocalDate data) {
        return idade(dataNascimento, data);
    }

    public static int idade(LocalDate nascimento, LocalDate data) {
        return Math.max(0, Period.between(nascimento, data).getYears());
    }

    /** Menores de 18 anos na data de referência exigem o nome do responsável. */
    public static boolean exigeResponsavel(LocalDate nascimento, LocalDate referencia) {
        return idade(nascimento, referencia) < MAIORIDADE;
    }

    /** Atualiza os dados cadastrais pela carga; devolve {@code true} se algo mudou. */
    public boolean atualizarCadastro(
            String novoNome,
            LocalDate novaDataNascimento,
            String novoResponsavel,
            String novoInstrumento,
            Instant agora) {
        if (nome.equals(novoNome)
                && dataNascimento.equals(novaDataNascimento)
                && Objects.equals(nomeResponsavel, novoResponsavel)
                && Objects.equals(instrumento, novoInstrumento)) {
            return false;
        }
        nome = novoNome;
        dataNascimento = novaDataNascimento;
        nomeResponsavel = novoResponsavel;
        instrumento = novoInstrumento;
        atualizadoEm = agora;
        return true;
    }

    public UUID id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public LocalDate dataNascimento() {
        return dataNascimento;
    }

    public byte[] cpfCifrado() {
        return cpfCifrado.clone();
    }

    public String cpfHash() {
        return cpfHash;
    }

    public String nomeResponsavel() {
        return nomeResponsavel;
    }

    public String instrumento() {
        return instrumento;
    }

    public boolean ativo() {
        return ativo;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }

    @Override
    public String toString() {
        return "Aluno[id=" + id + "]";
    }
}
