package br.com.felipeblaft.relatorios.dominio.relatorio;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatório de aula e sua máquina de estados (data-model.md §8.1). Toda mudança de conteúdo após a
 * primeira conferência gera nova versão; a aprovação exige a versão vigente e nenhuma etapa em
 * curso (Princípio III).
 */
public class RelatorioAula {

    /** No máximo duas rodadas de perguntas por relatório (FR-025). */
    public static final int MAXIMO_RODADAS = 2;

    public static final String FALHA_ESTRUTURACAO = "FALHA_ESTRUTURACAO";

    private final UUID id;
    private final UUID aulaId;
    private final UUID professorId;
    private final UUID alunoId;
    private final String transcricao;
    private ConteudoRelatorioAula conteudo;
    private List<String> perguntasPendentes;
    private int rodadasPerguntas;
    private StatusRelatorio status;
    private EtapaRelatorio etapa;
    private int versaoVigente;
    private final Canal canalOrigem;
    private String motivoFalha;
    private final Instant criadoEm;
    private Instant atualizadoEm;
    private Instant aprovadoEm;
    private Integer versaoAprovada;
    private Canal canalAprovacao;
    private Instant canceladoEm;
    private long tempoProcessamentoMs;
    private long versao;

    @SuppressWarnings("java:S107")
    public RelatorioAula(
            UUID id,
            UUID aulaId,
            UUID professorId,
            UUID alunoId,
            String transcricao,
            ConteudoRelatorioAula conteudo,
            List<String> perguntasPendentes,
            int rodadasPerguntas,
            StatusRelatorio status,
            EtapaRelatorio etapa,
            int versaoVigente,
            Canal canalOrigem,
            String motivoFalha,
            Instant criadoEm,
            Instant atualizadoEm,
            Instant aprovadoEm,
            Integer versaoAprovada,
            Canal canalAprovacao,
            Instant canceladoEm,
            long tempoProcessamentoMs,
            long versao) {
        this.id = Objects.requireNonNull(id);
        this.aulaId = Objects.requireNonNull(aulaId);
        this.professorId = Objects.requireNonNull(professorId);
        this.alunoId = Objects.requireNonNull(alunoId);
        this.transcricao = Objects.requireNonNull(transcricao);
        this.conteudo = conteudo == null ? ConteudoRelatorioAula.vazio() : conteudo;
        this.perguntasPendentes =
                perguntasPendentes == null ? List.of() : List.copyOf(perguntasPendentes);
        this.rodadasPerguntas = rodadasPerguntas;
        this.status = Objects.requireNonNull(status);
        this.etapa = etapa;
        this.versaoVigente = versaoVigente;
        this.canalOrigem = Objects.requireNonNull(canalOrigem);
        this.motivoFalha = motivoFalha;
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
        this.aprovadoEm = aprovadoEm;
        this.versaoAprovada = versaoAprovada;
        this.canalAprovacao = canalAprovacao;
        this.canceladoEm = canceladoEm;
        this.tempoProcessamentoMs = tempoProcessamentoMs;
        this.versao = versao;
    }

    /** Criado só depois de a transcrição dar certo (FR-017), já estruturando. */
    public static RelatorioAula criar(Aula aula, String transcricao, Canal canal, Instant agora) {
        if (transcricao == null || transcricao.isBlank()) {
            throw new IllegalArgumentException("transcrição vazia não cria relatório");
        }
        return new RelatorioAula(
                GeradorId.novo(),
                aula.id(),
                aula.professorId(),
                aula.alunoId(),
                transcricao.strip(),
                ConteudoRelatorioAula.vazio(),
                List.of(),
                0,
                StatusRelatorio.EM_PROCESSAMENTO,
                EtapaRelatorio.ESTRUTURANDO,
                0,
                canal,
                null,
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

    /** Recusa qualquer ação sobre relatório aprovado ou cancelado (FR-031, FR-032). */
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

    private void exigirStatus(StatusRelatorio esperado) {
        if (status != esperado) {
            throw new ExcecaoDominio(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
    }

    public void exigirPodeResponder() {
        exigirAberto();
        exigirSemEtapaEmCurso();
        exigirStatus(StatusRelatorio.AGUARDANDO_RESPOSTAS);
    }

    /** Pedir alteração exige conferência sem etapa em curso e a versão vigente (FR-033). */
    public void exigirPodeAlterar(int versaoBase) {
        exigirAberto();
        exigirSemEtapaEmCurso();
        exigirStatus(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        exigirVersaoVigente(versaoBase);
    }

    public void exigirVersaoVigente(int versaoApresentada) {
        if (versaoApresentada != versaoVigente) {
            throw new ExcecaoDominio(
                    CodigoErro.VERSAO_DESATUALIZADA, Map.of("versaoVigente", versaoVigente));
        }
    }

    public void exigirPodeTentarDeNovo() {
        exigirAberto();
        exigirSemEtapaEmCurso();
        exigirStatus(StatusRelatorio.FALHA_PROCESSAMENTO);
    }

    // ------------------------------------------------------------ transições

    /**
     * Resultado da estruturação. Com perguntas (e rodada disponível) aguarda respostas; senão vai
     * para conferência na versão 1. Devolve {@code true} se a versão 1 deve ser criada.
     */
    public boolean registrarEstruturacao(
            ConteudoRelatorioAula novoConteudo, List<String> perguntas, Instant agora) {
        exigirInterno(StatusRelatorio.EM_PROCESSAMENTO);
        return aplicarConteudoComPerguntas(novoConteudo, perguntas, agora);
    }

    private boolean aplicarConteudoComPerguntas(
            ConteudoRelatorioAula novoConteudo, List<String> perguntas, Instant agora) {
        conteudo = Objects.requireNonNull(novoConteudo);
        List<String> validas = ConteudoRelatorioAula.itens(perguntas);
        etapa = null;
        motivoFalha = null;
        atualizadoEm = agora;
        if (!validas.isEmpty() && rodadasPerguntas < MAXIMO_RODADAS) {
            rodadasPerguntas++;
            perguntasPendentes = validas;
            status = StatusRelatorio.AGUARDANDO_RESPOSTAS;
            return false;
        }
        perguntasPendentes = List.of();
        status = StatusRelatorio.AGUARDANDO_CONFERENCIA;
        versaoVigente = 1;
        return true;
    }

    public void falharEstruturacao(Instant agora) {
        exigirInterno(StatusRelatorio.EM_PROCESSAMENTO);
        status = StatusRelatorio.FALHA_PROCESSAMENTO;
        motivoFalha = FALHA_ESTRUTURACAO;
        etapa = null;
        atualizadoEm = agora;
    }

    public void prepararNovaTentativa(Instant agora) {
        exigirPodeTentarDeNovo();
        status = StatusRelatorio.EM_PROCESSAMENTO;
        etapa = EtapaRelatorio.ESTRUTURANDO;
        motivoFalha = null;
        atualizadoEm = agora;
    }

    public void iniciarEtapa(EtapaRelatorio novaEtapa, Instant agora) {
        etapa = Objects.requireNonNull(novaEtapa);
        atualizadoEm = agora;
    }

    public void concluirEtapa(Instant agora) {
        etapa = null;
        atualizadoEm = agora;
    }

    /**
     * Resposta incorporada (FR-025): se ainda houver perguntas e rodada disponível, nova rodada;
     * senão, conferência na versão 1. Devolve {@code true} se a versão 1 deve ser criada.
     */
    public boolean incorporarResposta(
            ConteudoRelatorioAula novoConteudo, List<String> novasPerguntas, Instant agora) {
        exigirInterno(StatusRelatorio.AGUARDANDO_RESPOSTAS);
        return aplicarConteudoComPerguntas(novoConteudo, novasPerguntas, agora);
    }

    /** Alteração aplicada: nova versão (N+1). Devolve o número da nova versão. */
    public int aplicarAlteracao(ConteudoRelatorioAula novoConteudo, Instant agora) {
        exigirInterno(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        conteudo = Objects.requireNonNull(novoConteudo);
        versaoVigente++;
        etapa = null;
        atualizadoEm = agora;
        return versaoVigente;
    }

    /** Aprovação explícita da versão apresentada (FR-028 a FR-030). */
    public void aprovar(int versaoConfirmada, Canal canal, Instant agora) {
        exigirAberto();
        exigirSemEtapaEmCurso();
        exigirStatus(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        exigirVersaoVigente(versaoConfirmada);
        status = StatusRelatorio.APROVADO;
        aprovadoEm = agora;
        versaoAprovada = versaoVigente;
        canalAprovacao = Objects.requireNonNull(canal);
        atualizadoEm = agora;
    }

    /**
     * Cancelamento (FR-031). Em processamento, só é permitido pelo descarte na retomada; o
     * resultado de um trabalho em curso é descartado.
     */
    public void cancelar(Instant agora, boolean descarteNaRetomada) {
        exigirAberto();
        if (status == StatusRelatorio.EM_PROCESSAMENTO && !descarteNaRetomada) {
            throw new ExcecaoDominio(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        status = StatusRelatorio.CANCELADO;
        canceladoEm = agora;
        etapa = null;
        atualizadoEm = agora;
    }

    public void somarTempoProcessamento(long milissegundos) {
        tempoProcessamentoMs += Math.max(0, milissegundos);
    }

    private void exigirInterno(StatusRelatorio esperado) {
        if (status != esperado) {
            throw new IllegalStateException("transição inválida a partir de " + status);
        }
    }

    public void registrarVersaoPersistida(long versaoGravada) {
        this.versao = versaoGravada;
    }

    // ------------------------------------------------------------ leitura

    public UUID id() {
        return id;
    }

    public UUID aulaId() {
        return aulaId;
    }

    public UUID professorId() {
        return professorId;
    }

    public UUID alunoId() {
        return alunoId;
    }

    public String transcricao() {
        return transcricao;
    }

    public ConteudoRelatorioAula conteudo() {
        return conteudo;
    }

    public List<String> perguntasPendentes() {
        return perguntasPendentes;
    }

    public int rodadasPerguntas() {
        return rodadasPerguntas;
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

    public String motivoFalha() {
        return motivoFalha;
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
        return "RelatorioAula[id=" + id + ", status=" + status + ", versao=" + versaoVigente + "]";
    }
}
