package br.com.felipeblaft.relatorios.dominio.cadastro;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Professor (data-model.md §1). O código de acesso é guardado só como hash. */
public class Professor {

    private final UUID id;
    private String nome;
    private final String email;
    private boolean ativo;
    private String codigoAcessoHash;
    private Instant codigoAcessoExpiraEm;
    private Instant codigoAcessoRenovadoEm;
    private final Instant criadoEm;
    private Instant atualizadoEm;

    public Professor(
            UUID id,
            String nome,
            String email,
            boolean ativo,
            String codigoAcessoHash,
            Instant codigoAcessoExpiraEm,
            Instant codigoAcessoRenovadoEm,
            Instant criadoEm,
            Instant atualizadoEm) {
        this.id = Objects.requireNonNull(id);
        this.nome = Objects.requireNonNull(nome);
        this.email = Objects.requireNonNull(email);
        this.ativo = ativo;
        this.codigoAcessoHash = codigoAcessoHash;
        this.codigoAcessoExpiraEm = codigoAcessoExpiraEm;
        this.codigoAcessoRenovadoEm = codigoAcessoRenovadoEm;
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
    }

    public static Professor novo(String nome, String email, boolean ativo, Instant agora) {
        return new Professor(
                GeradorId.novo(),
                nome,
                normalizarEmail(email),
                ativo,
                null,
                null,
                null,
                agora,
                agora);
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Código presente, dentro da validade e professor ativo. */
    public boolean codigoValidoEm(Instant instante) {
        return ativo
                && codigoAcessoHash != null
                && codigoAcessoExpiraEm != null
                && instante.isBefore(codigoAcessoExpiraEm);
    }

    public boolean codigoExpiradoEm(Instant instante) {
        return codigoAcessoHash != null
                && codigoAcessoExpiraEm != null
                && !instante.isBefore(codigoAcessoExpiraEm);
    }

    /**
     * Renova a validade do código (FR-005). Para limitar escritas, só renova se a última renovação
     * tiver 1 hora ou mais. Devolve {@code true} se o estado mudou e precisa ser gravado.
     */
    public boolean renovarValidade(Instant agora, Duration validade) {
        if (codigoAcessoHash == null) {
            return false;
        }
        if (codigoAcessoRenovadoEm != null
                && Duration.between(codigoAcessoRenovadoEm, agora)
                                .compareTo(ParametrosNegocio.INTERVALO_RENOVACAO_CODIGO)
                        < 0) {
            return false;
        }
        codigoAcessoExpiraEm = agora.plus(validade);
        codigoAcessoRenovadoEm = agora;
        atualizadoEm = agora;
        return true;
    }

    /** Emite ou reemite o código: substitui o hash e reinicia a validade. */
    public void substituirCodigo(String novoHash, Instant agora, Duration validade) {
        codigoAcessoHash = Objects.requireNonNull(novoHash);
        codigoAcessoExpiraEm = agora.plus(validade);
        codigoAcessoRenovadoEm = agora;
        atualizadoEm = agora;
    }

    /** Atualiza nome e situação pela carga; devolve {@code true} se algo mudou. */
    public boolean atualizarCadastro(String novoNome, boolean novoAtivo, Instant agora) {
        if (nome.equals(novoNome) && ativo == novoAtivo) {
            return false;
        }
        nome = novoNome;
        ativo = novoAtivo;
        atualizadoEm = agora;
        return true;
    }

    /** Primeiro nome, usado na saudação ("Olá, Clara"). */
    public String primeiroNome() {
        return nome.strip().split("\\s+")[0];
    }

    public UUID id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public String email() {
        return email;
    }

    public boolean ativo() {
        return ativo;
    }

    public String codigoAcessoHash() {
        return codigoAcessoHash;
    }

    public Instant codigoAcessoExpiraEm() {
        return codigoAcessoExpiraEm;
    }

    public Instant codigoAcessoRenovadoEm() {
        return codigoAcessoRenovadoEm;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }
}
