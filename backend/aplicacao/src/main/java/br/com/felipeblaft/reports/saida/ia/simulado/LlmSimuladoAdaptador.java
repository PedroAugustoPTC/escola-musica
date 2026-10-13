package br.com.felipeblaft.reports.saida.ia.simulado;

import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.port.AiLessonReportOutput;
import br.com.felipeblaft.reports.port.AiSemesterReportOutput;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.SemesterConsolidationInput;
import br.com.felipeblaft.reports.port.SemesterRevisionInput;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM simulado e determinístico ({@code PROVEDORES_IA=simulado}), usado nos testes ponta a ponta e
 * em desenvolvimento: a primeira estruturação traz perguntas; a incorporação não traz; a revisão
 * muda a observação conforme o pedido e sinaliza pedidos de aprovar ou cancelar.
 */
public class LlmSimuladoAdaptador implements LlmPort {

    public static final String PROVEDOR = "simulado/llm";

    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /** Respostas de outras tarefas (ex.: semestral), registradas pela história correspondente. */
    private BiFunction<TaskLlm, TaskInput, Object> complementar;

    public void registrarComplementar(BiFunction<TaskLlm, TaskInput, Object> resposta) {
        this.complementar = resposta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ResponseLlm<T> generateJson(TaskLlm tarefa, TaskInput entrada, Class<T> tipoSaida) {
        Object saida =
                switch (entrada) {
                    case TaskInput.LessonStructuring e -> estruturacao();
                    case TaskInput.AnswerIncorporation e -> incorporacao(e);
                    case TaskInput.LessonRevision e -> revisao(e);
                    case SemesterConsolidationInput e -> consolidacao();
                    case SemesterRevisionInput e -> revisaoSemestral(e);
                    default ->
                            Objects.requireNonNull(complementar, "tarefa sem simulação")
                                    .apply(tarefa, entrada);
                };
        return new ResponseLlm<>(
                (T) saida,
                tarefa.name().toLowerCase().replace('_', '-') + ".v1",
                0,
                0,
                BigDecimal.ZERO,
                PROVEDOR,
                1);
    }

    private static AiLessonReportOutput estruturacao() {
        try (InputStream entrada =
                LlmSimuladoAdaptador.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/estruturacao-aula.json")) {
            return JSON.readValue(entrada, AiLessonReportOutput.class);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    private static AiSemesterReportOutput consolidacao() {
        try (InputStream entrada =
                LlmSimuladoAdaptador.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/consolidacao-semestral.json")) {
            return JSON.readValue(entrada, AiSemesterReportOutput.class);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    /** Revisão do semestral: o pedido vira o parecer final; aprovar/cancelar só são sinalizados. */
    private static AiSemesterReportOutput revisaoSemestral(SemesterRevisionInput entrada) {
        String pedido = StudentSearch.normalize(entrada.pedido());
        SemesterReportContent atual = entrada.conteudoAtual();
        if (pedido.contains("aprovar") || pedido.contains("aprova")) {
            return AiSemesterReportOutput.of(atual, true, "APROVAR");
        }
        if (pedido.contains("cancelar") || pedido.contains("descarta")) {
            return AiSemesterReportOutput.of(atual, true, "CANCELAR");
        }
        SemesterReportContent alterado =
                new SemesterReportContent(
                        atual.frequenciaEEstudo(),
                        atual.tecnica(),
                        atual.musicalidade(),
                        atual.leituraEMemorizacao(),
                        atual.pontosDeAtencao(),
                        atual.estrategiasPedagogicas(),
                        atual.acompanhamentoFamiliar(),
                        atual.planejamentoProximoSemestre(),
                        entrada.pedido().strip());
        return AiSemesterReportOutput.of(alterado, false, AiLessonReportOutput.NONE);
    }

    private static AiLessonReportOutput incorporacao(TaskInput.AnswerIncorporation entrada) {
        LessonReportContent atual = entrada.conteudoAtual();
        String observacoes = juntar(atual.observacoes(), entrada.resposta());
        return saida(atual, observacoes, false, "NENHUM");
    }

    private static AiLessonReportOutput revisao(TaskInput.LessonRevision entrada) {
        String pedido = StudentSearch.normalize(entrada.pedido());
        LessonReportContent atual = entrada.conteudoAtual();
        if (pedido.contains("aprovar") || pedido.contains("aprova")) {
            return saida(atual, atual.observacoes(), true, "APROVAR");
        }
        if (pedido.contains("cancelar") || pedido.contains("descarta")) {
            return saida(atual, atual.observacoes(), true, "CANCELAR");
        }
        return saida(atual, entrada.pedido().strip(), false, "NENHUM");
    }

    private static AiLessonReportOutput saida(
            LessonReportContent base,
            String observacoes,
            boolean semAlteracao,
            String pedidoDeAcao) {
        return new AiLessonReportOutput(
                base.conteudosTrabalhados(),
                base.evolucao(),
                base.dificuldades(),
                base.atividadesPropostas(),
                observacoes,
                List.of(),
                semAlteracao,
                pedidoDeAcao);
    }

    private static String juntar(String atual, String acrescimo) {
        return atual == null || atual.isBlank()
                ? acrescimo.strip()
                : atual + " " + acrescimo.strip();
    }
}
