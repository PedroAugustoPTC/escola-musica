package br.com.felipeblaft.relatorios.dominio.semestral;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatório semestral (data-model.md §9). Nasce em conferência na versão 1, só depois de a
 * consolidação dar certo (FR-045); alterar, aprovar e cancelar seguem as regras do relatório de
 * aula (Princípio III).
 */
public class RelatorioSemestral {

    private final UUID id;
    private final UUID professorId;
    private final UUID alunoId;
    private final PeriodoSemestral periodo;
    private final int quantidadeAulasConsideradas;
    private final int quantidadeRelatoriosEmAberto;
    private final InformacoesGerais informacoesGerais;
    private ConteudoRelatorioSemestral conteudo;
    private StatusRelatorio status;
    private EtapaRelatorio etapa;
    private int versaoVigente;
    private final Canal canalOrigem;
    private final Instant criadoEm;
    private Instant atualizadoEm;
    private Instant aprovadoEm;
    private Integer versaoAprovada;
    private Canal canalAprovacao;
    private Instant canceladoEm;
    private long tempoProcessamentoMs;
    private long versao;

    @SuppressWarnings("java:S107")
    public RelatorioSemestral(
            UUID id,
            UUID professorId,
            UUID alunoId,
            PeriodoSemestral periodo,
            int quantidadeAulasConsideradas,
            int quantidadeRelatoriosEmAberto,
            InformacoesGerais informacoesGerais,
            ConteudoRelatorioSemestral conteudo,
            StatusRelatorio status,
            EtapaRelatorio etapa,
            int versaoVigente,
            Canal canalOrigem,
            Instant criadoEm,
            Instant atualizadoEm,
            Instant aprovadoEm,
            Integer versaoAprovada,
            Canal canalAprovacao,
            Instant canceladoEm,
            long tempoProcessamentoMs,
            long versao) {
        this.id = Objects.requireNonNull(id);
        this.professorId = Objects.requireNonNull(professorId);
        this.alunoId = Objects.requireNonNull(alunoId);
        this.periodo = Objects.requireNonNull(periodo);
        if (quantidadeAulasConsideradas < 1) {
            throw new IllegalArgumentException("o semestral considera ao menos uma aula aprovada");
        }
        this.quantidadeAulasConsideradas = quantidadeAulasConsideradas;
        this.quantidadeRelatoriosEmAberto = Math.max(0, quantidadeRelatoriosEmAberto);
        this.informacoesGerais = Objects.requireNonNull(informacoesGerais);
        this.conteudo = conteudo == null ? ConteudoRelatorioSemestral.vazio() : conteudo;
        this.status = Objects.requireNonNull(status);
        this.etapa = etapa;
        this.versaoVigente = versaoVigente;
        this.canalOrigem = Objects.requireNonNull(canalOrigem);
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
        this.aprovadoEm = aprovadoEm;
        this.versaoAprovada = versaoAprovada;
        this.canalAprovacao = canalAprovacao;
        this.canceladoEm = canceladoEm;
        this.tempoProcessamentoMs = tempoProcessamentoMs;
        this.versao = versao;
    }

    /** Consolidação bem-sucedida: conferência na versão 1. */
    @SuppressWarnings("java:S107")
    public static RelatorioSemestral criar(
            UUID professorId,
            UUID alunoId,
            PeriodoSemestral periodo,
            int quantidadeAulas,
            int quantidadeEmAberto,
            InformacoesGerais informacoesGerais,
            ConteudoRelatorioSemestral conteudo,
            Canal canal,
            Instant agora) {
        return new RelatorioSemestral(
                GeradorId.novo(),
                professorId,
                alunoId,
                periodo,
                quantidadeAulas,
                quantidadeEmAberto,
                informacoesGerais,
                conteudo,
                StatusRelatorio.AGUARDANDO_CONFERENCIA,
                null,
                1,
                canal,
                agora,
                agora,
                null,
                null,
                null,
                null,
                0,
                0);
    }

    // ------------------------------------------------------------ verificações

    public void exigirAberto() {
        if (status == StatusRelatorio.APROVADO) {
            throw new ExcecaoDominio(CodigoErro.RELATORIO_JA_APROVADO);
        }
        if (status == StatusRelatorio.CANCELADO) {
            throw new ExcecaoDominio(CodigoErro.RELATORIO_CANCELADO);
        }
    }

    private void exigirSemEtapaEmCurso() {
        if (etapa != null) {
            throw new ExcecaoDominio(
                    CodigoErro.PROCESSAMENTO_EM_ANDAMENTO, Map.of("etapa", etapa.name()));
        }
    }

    public void exigirVersaoVigente(int versaoApresentada) {
        if (versaoApresentada != versaoVigente) {
            throw new ExcecaoDominio(
                    CodigoErro.VERSAO_DESATUALIZADA, Map.of("versaoVigente", versaoVigente));
        }
    }

    public void exigirPodeAlterar(int versaoBase) {
        exigirAberto();
        exigirSemEtapaEmCurso();
        exigirVersaoVigente(versaoBase);
    }

    // ------------------------------------------------------------ transições

    public void iniciarEtapa(EtapaRelatorio novaEtapa, Instant agora) {
        etapa = Objects.requireNonNull(novaEtapa);
        atualizadoEm = agora;
    }

    public void concluirEtapa(Instant agora) {
        etapa = null;
        atualizadoEm = agora;
    }

    /** Alteração aplicada: nova versão (N+1). Devolve o número da nova versão. */
    public int aplicarAlteracao(ConteudoRelatorioSemestral novoConteudo, Instant agora) {
        if (status != StatusRelatorio.AGUARDANDO_CONFERENCIA) {
            throw new IllegalStateException("transição inválida a partir de " + status);
        }
        conteudo = Objects.requireNonNull(novoConteudo);
        versaoVigente++;
        etapa = null;
        atualizadoEm = agora;
        return versaoVigente;
    }

    public void aprovar(int versaoConfirmada, Canal canal, Instant agora) {
        exigirAberto();
        exigirSemEtapaEmCurso();
        exigirVersaoVigente(versaoConfirmada);
        status = StatusRelatorio.APROVADO;
        aprovadoEm = agora;
        versaoAprovada = versaoVigente;
        canalAprovacao = Objects.requireNonNull(canal);
        atualizadoEm = agora;
    }

    public void cancelar(Instant agora) {
        exigirAberto();
        status = StatusRelatorio.CANCELADO;
        canceladoEm = agora;
        etapa = null;
        atualizadoEm = agora;
    }

    public void somarTempoProcessamento(long milissegundos) {
        tempoProcessamentoMs += Math.max(0, milissegundos);
    }

    public void registrarVersaoPersistida(long versaoGravada) {
        this.versao = versaoGravada;
    }

    // ------------------------------------------------------------ leitura

    public UUID id() {
        return id;
    }

    public UUID professorId() {
        return professorId;
    }

    public UUID alunoId() {
        return alunoId;
    }

    public PeriodoSemestral periodo() {
        return periodo;
    }

    public int quantidadeAulasConsideradas() {
        return quantidadeAulasConsideradas;
    }

    public int quantidadeRelatoriosEmAberto() {
        return quantidadeRelatoriosEmAberto;
    }

    public InformacoesGerais informacoesGerais() {
        return informacoesGerais;
    }

    public ConteudoRelatorioSemestral conteudo() {
        return conteudo;
    }

    public StatusRelatorio status() {
        return status;
    }

    public EtapaRelatorio etapa() {
        return etapa;
    }

    public int versaoVigente() {
        return versaoVigente;
    }

    public Canal canalOrigem() {
        return canalOrigem;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }

    public Instant aprovadoEm() {
        return aprovadoEm;
    }

    public Integer versaoAprovada() {
        return versaoAprovada;
    }

    public Canal canalAprovacao() {
        return canalAprovacao;
    }

    public Instant canceladoEm() {
        return canceladoEm;
    }

    public long tempoProcessamentoMs() {
        return tempoProcessamentoMs;
    }

    public long versao() {
        return versao;
    }

    @Override
    public String toString() {
        return "RelatorioSemestral[id="
                + id
                + ", periodo="
                + periodo.chave()
                + ", status="
                + status
                + "]";
    }
}
