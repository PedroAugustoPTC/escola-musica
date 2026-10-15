package br.com.felipeblaft.reports.support;

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
public class ProgrammableLlmTest implements Answer<Object> {

    private final Deque<List<String>> incorporationQuestions = new ArrayDeque<>();
    private final List<TaskInput.AnswerIncorporation> incorporations = new ArrayList<>();

    /** Perguntas que as próximas incorporações devolvem, uma lista por chamada. */
    @SafeVarargs
    public final ProgrammableLlmTest questionsInIncorporations(List<String>... rounds) {
        incorporationQuestions.addAll(List.of(rounds));
        return this;
    }

    /** Entradas recebidas pelas incorporações, na ordem. */
    public List<TaskInput.AnswerIncorporation> incorporations() {
        return List.copyOf(incorporations);
    }

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        Object real = invocation.callRealMethod();
        TaskLlm task = invocation.getArgument(0);
        if (task != TaskLlm.ANSWER_INCORPORATION) {
            return real;
        }
        incorporations.add((TaskInput.AnswerIncorporation) invocation.getArgument(1));
        if (incorporationQuestions.isEmpty()) {
            return real;
        }
        ResponseLlm<?> response = (ResponseLlm<?>) real;
        AiLessonReportOutput output = (AiLessonReportOutput) response.output();
        return new ResponseLlm<>(
                new AiLessonReportOutput(
                        output.workedTopics(),
                        output.progress(),
                        output.difficulties(),
                        output.proposedActivities(),
                        output.observations(),
                        incorporationQuestions.poll(),
                        output.noContentChange(),
                        output.actionRequest()),
                response.versionPrompt(),
                response.inputTokens(),
                response.outputTokens(),
                response.estimatedCost(),
                response.modelProvider(),
                response.durationMs());
    }
}
