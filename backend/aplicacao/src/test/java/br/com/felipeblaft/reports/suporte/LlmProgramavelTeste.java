package br.com.felipeblaft.reports.suporte;

import br.com.felipeblaft.reports.port.AiLessonReportOutput;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Programa o {@code LlmPorta} simulado dos testes de integração (por {@code doAnswer} num spy do
 * bean): a IA simulada só pergunta na estruturação, e aqui cada incorporação de respostas devolve,
 * em ordem, as perguntas programadas. Tudo mais segue o comportamento simulado.
 */
public class LlmProgramavelTeste implements Answer<Object> {

    private final Deque<List<String>> perguntasDasIncorporacoes = new ArrayDeque<>();
    private final List<TaskInput.AnswerIncorporation> incorporacoes = new ArrayList<>();

    /** Perguntas que as próximas incorporações devolvem, uma lista por chamada. */
    @SafeVarargs
    public final LlmProgramavelTeste perguntasNasIncorporacoes(List<String>... rodadas) {
        perguntasDasIncorporacoes.addAll(List.of(rodadas));
        return this;
    }

    /** Entradas recebidas pelas incorporações, na ordem. */
    public List<TaskInput.AnswerIncorporation> incorporacoes() {
        return List.copyOf(incorporacoes);
    }

    @Override
    public Object answer(InvocationOnMock invocacao) throws Throwable {
        Object real = invocacao.callRealMethod();
        TaskLlm tarefa = invocacao.getArgument(0);
        if (tarefa != TaskLlm.INCORPORACAO_RESPOSTAS) {
            return real;
        }
        incorporacoes.add((TaskInput.AnswerIncorporation) invocacao.getArgument(1));
        if (perguntasDasIncorporacoes.isEmpty()) {
            return real;
        }
        ResponseLlm<?> resposta = (ResponseLlm<?>) real;
        AiLessonReportOutput saida = (AiLessonReportOutput) resposta.output();
        return new ResponseLlm<>(
                new AiLessonReportOutput(
                        saida.conteudosTrabalhados(),
                        saida.evolucao(),
                        saida.dificuldades(),
                        saida.atividadesPropostas(),
                        saida.observacoes(),
                        perguntasDasIncorporacoes.poll(),
                        saida.semAlteracaoDeConteudo(),
                        saida.pedidoDeAcao()),
                resposta.versionPrompt(),
                resposta.inputTokens(),
                resposta.outputTokens(),
                resposta.estimatedCost(),
                resposta.modelProvider(),
                resposta.durationMs());
    }
}
