package br.com.felipeblaft.relatorios.dominio.trabalho;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Trabalho da fila persistida (data-model.md §13, R-06): fonte das etapas mostradas nos dois canais
 * e do resultado entregue no canal em que foi pedido (FR-021).
 */
public class TrabalhoProcessamento {

    /** Depois de 3 minutos sem conclusão, os canais avisam que está demorando. */
    public static final Duration LIMITE_DEMORA = Duration.ofMinutes(3);

    public static final int MAXIMO_TENTATIVAS = 2;

    // chaves conhecidas dos parâmetros
    public static final String TEXTO = "texto";
    public static final String VERSAO_BASE = "versaoBase";
    public static final String TEXTO_ENTENDIDO = "textoEntendido";
    public static final String ARQUIVO_CANAL = "arquivoCanal";
    public static final String ARQUIVO_CANAL_UNICO = "arquivoCanalUnico";
    public static final String ARQUIVO_MIME = "arquivoMime";
    public static final String ARQUIVO_TAMANHO = "arquivoTamanho";
    public static final String MENSAGEM_ETAPA = "mensagemEtapa";
    public static final String PERIODO_CHAVE = "periodoChave";
    public static final String CONFIRMAR_EM_ABERTO = "confirmarMesmoComEmAberto";

    private final UUID id;
    private final TipoTrabalho tipo;
    private final UUID professorId;
    private final UUID alunoId;
    private TipoRelatorio relatorioTipo;
    private UUID relatorioId;
    private final Canal canalSolicitacao;
    private final Meio meio;
    private final Map<String, String> parametros;
    private String arquivoTemporario;
    private StatusTrabalho status;
    private EtapaTrabalho etapa;
    private ResultadoTrabalho resultado;
    private UUID relatorioResultanteId;
    private int tentativas;
    private boolean avisoDemoraEnviado;
    private final Instant criadoEm;
    private Instant iniciadoEm;
    private Instant concluidoEm;

    @SuppressWarnings("java:S107")
    public TrabalhoProcessamento(
            UUID id,
            TipoTrabalho tipo,
            UUID professorId,
            UUID alunoId,
            TipoRelatorio relatorioTipo,
            UUID relatorioId,
            Canal canalSolicitacao,
            Meio meio,
            Map<String, String> parametros,
            String arquivoTemporario,
            StatusTrabalho status,
            EtapaTrabalho etapa,
            ResultadoTrabalho resultado,
            UUID relatorioResultanteId,
            int tentativas,
            boolean avisoDemoraEnviado,
            Instant criadoEm,
            Instant iniciadoEm,
            Instant concluidoEm) {
        this.id = Objects.requireNonNull(id);
        this.tipo = Objects.requireNonNull(tipo);
        this.professorId = Objects.requireNonNull(professorId);
        this.alunoId = Objects.requireNonNull(alunoId);
        this.relatorioTipo = relatorioTipo;
        this.relatorioId = relatorioId;
        this.canalSolicitacao = Objects.requireNonNull(canalSolicitacao);
        this.meio = Objects.requireNonNull(meio);
        this.parametros = new LinkedHashMap<>(parametros == null ? Map.of() : parametros);
        this.arquivoTemporario = arquivoTemporario;
        this.status = Objects.requireNonNull(status);
        this.etapa = Objects.requireNonNull(etapa);
        this.resultado = resultado;
        this.relatorioResultanteId = relatorioResultanteId;
        this.tentativas = tentativas;
        this.avisoDemoraEnviado = avisoDemoraEnviado;
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.iniciadoEm = iniciadoEm;
        this.concluidoEm = concluidoEm;
    }

    public static TrabalhoProcessamento novo(
            TipoTrabalho tipo,
            UUID professorId,
            UUID alunoId,
            TipoRelatorio relatorioTipo,
            UUID relatorioId,
            Canal canal,
            Meio meio,
            EtapaTrabalho etapaInicial,
            Instant agora) {
        return new TrabalhoProcessamento(
                GeradorId.novo(),
                tipo,
                professorId,
                alunoId,
                relatorioTipo,
                relatorioId,
                canal,
                meio,
                Map.of(),
                null,
                StatusTrabalho.PENDENTE,
                etapaInicial,
                null,
                null,
                0,
                false,
                agora,
                null,
                null);
    }

    public void definirParametro(String chave, String valor) {
        if (valor == null) {
            parametros.remove(chave);
        } else {
            parametros.put(chave, valor);
        }
    }

    public Optional<String> parametro(String chave) {
        return Optional.ofNullable(parametros.get(chave));
    }

    public void definirArquivoTemporario(String caminho) {
        this.arquivoTemporario = caminho;
    }

    public void iniciar(Instant agora) {
        if (status != StatusTrabalho.PENDENTE) {
            throw new IllegalStateException("trabalho não está pendente");
        }
        status = StatusTrabalho.EM_EXECUCAO;
        tentativas++;
        if (iniciadoEm == null) {
            iniciadoEm = agora;
        }
    }

    public void avancar(EtapaTrabalho novaEtapa) {
        etapa = Objects.requireNonNull(novaEtapa);
    }

    public void vincularRelatorio(TipoRelatorio tipo, UUID relatorio) {
        this.relatorioTipo = tipo;
        this.relatorioId = relatorio;
        this.relatorioResultanteId = relatorio;
    }

    public void concluir(ResultadoTrabalho resultadoFinal, Instant agora) {
        status = resultadoFinal.sucesso() ? StatusTrabalho.CONCLUIDO : StatusTrabalho.FALHOU;
        if (resultadoFinal == ResultadoTrabalho.DESCARTADO) {
            status = StatusTrabalho.DESCARTADO;
        }
        resultado = resultadoFinal;
        if (resultadoFinal.sucesso()) {
            etapa = EtapaTrabalho.PRONTO;
        }
        concluidoEm = agora;
    }

    /** Descarte pela retomada ("descartar e começar um novo"): o resultado posterior é ignorado. */
    public boolean descartar(Instant agora) {
        if (!status.emCurso()) {
            return false;
        }
        status = StatusTrabalho.DESCARTADO;
        resultado = ResultadoTrabalho.DESCARTADO;
        concluidoEm = agora;
        return true;
    }

    /** Volta à fila (trabalho travado recuperado após reinício). */
    public void devolverParaFila() {
        if (status == StatusTrabalho.EM_EXECUCAO) {
            status = StatusTrabalho.PENDENTE;
        }
    }

    public boolean demorando(Instant agora) {
        return status.emCurso() && !agora.isBefore(criadoEm.plus(LIMITE_DEMORA));
    }

    public void marcarAvisoDemora() {
        this.avisoDemoraEnviado = true;
    }

    public long duracaoMs() {
        if (iniciadoEm == null || concluidoEm == null) {
            return 0;
        }
        return Duration.between(iniciadoEm, concluidoEm).toMillis();
    }

    public UUID id() {
        return id;
    }

    public TipoTrabalho tipo() {
        return tipo;
    }

    public UUID professorId() {
        return professorId;
    }

    public UUID alunoId() {
        return alunoId;
    }

    public TipoRelatorio relatorioTipo() {
        return relatorioTipo;
    }

    public UUID relatorioId() {
        return relatorioId;
    }

    public Canal canalSolicitacao() {
        return canalSolicitacao;
    }

    public Meio meio() {
        return meio;
    }

    public Map<String, String> parametros() {
        return Map.copyOf(parametros);
    }

    public String arquivoTemporario() {
        return arquivoTemporario;
    }

    public StatusTrabalho status() {
        return status;
    }

    public EtapaTrabalho etapa() {
        return etapa;
    }

    public ResultadoTrabalho resultado() {
        return resultado;
    }

    public UUID relatorioResultanteId() {
        return relatorioResultanteId;
    }

    public int tentativas() {
        return tentativas;
    }

    public boolean avisoDemoraEnviado() {
        return avisoDemoraEnviado;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant iniciadoEm() {
        return iniciadoEm;
    }

    public Instant concluidoEm() {
        return concluidoEm;
    }

    @Override
    public String toString() {
        return "TrabalhoProcessamento[id=" + id + ", tipo=" + tipo + ", status=" + status + "]";
    }
}
