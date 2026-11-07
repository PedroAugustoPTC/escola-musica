package br.com.felipeblaft.reports.outbound.ai.simulated;

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
public class SimulatedLlmAdapter implements LlmPort {

    public static final String PROVIDER = "simulated/llm";

    @Override
    public String providerName() {
        return PROVIDER;
    }

    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /** Respostas de outras tarefas (ex.: semestral), registradas pela história correspondente. */
    private BiFunction<TaskLlm, TaskInput, Object> complement;

    public void registerComplement(BiFunction<TaskLlm, TaskInput, Object> response) {
        this.complement = response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ResponseLlm<T> generateJson(TaskLlm task, TaskInput input, Class<T> outputType) {
        Object output =
                switch (input) {
                    case TaskInput.LessonStructuring e -> structuring();
                    case TaskInput.AnswerIncorporation e -> incorporation(e);
                    case TaskInput.LessonRevision e -> revision(e);
                    case SemesterConsolidationInput e -> consolidation();
                    case SemesterRevisionInput e -> semesterRevision(e);
                    default ->
                            Objects.requireNonNull(complement, "tarefa sem simulação")
                                    .apply(task, input);
                };
        return new ResponseLlm<>(
                (T) output,
                task.name().toLowerCase().replace('_', '-') + ".v1",
                0,
                0,
                BigDecimal.ZERO,
                PROVIDER,
                1);
    }

    private static AiLessonReportOutput structuring() {
        try (InputStream input =
                SimulatedLlmAdapter.class
                        .getClassLoader()
                        .getResourceAsStream("ai/simulated/lesson-structuring.json")) {
            return JSON.readValue(input, AiLessonReportOutput.class);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static AiSemesterReportOutput consolidation() {
        try (InputStream input =
                SimulatedLlmAdapter.class
                        .getClassLoader()
                        .getResourceAsStream("ai/simulated/semester-consolidation.json")) {
            return JSON.readValue(input, AiSemesterReportOutput.class);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Revisão do semestral: o pedido vira o parecer final; aprovar/cancelar só são sinalizados. */
    private static AiSemesterReportOutput semesterRevision(SemesterRevisionInput input) {
        String request = StudentSearch.normalize(input.request());
        SemesterReportContent current = input.currentContent();
        if (request.contains("aprovar") || request.contains("approves")) {
            return AiSemesterReportOutput.of(current, true, "APPROVE");
        }
        if (request.contains("cancelar") || request.contains("discards")) {
            return AiSemesterReportOutput.of(current, true, "CANCEL");
        }
        SemesterReportContent changed =
                new SemesterReportContent(
                        current.attendanceAndStudy(),
                        current.technique(),
                        current.musicality(),
                        current.readingAndMemorization(),
                        current.attentionPoints(),
                        current.pedagogicalStrategies(),
                        current.familySupport(),
                        current.nextSemesterPlan(),
                        input.request().strip());
        return AiSemesterReportOutput.of(changed, false, AiLessonReportOutput.NONE);
    }

    private static AiLessonReportOutput incorporation(TaskInput.AnswerIncorporation input) {
        LessonReportContent current = input.currentContent();
        String observations = join(current.observations(), input.response());
        return output(current, observations, false, "NONE");
    }

    private static AiLessonReportOutput revision(TaskInput.LessonRevision input) {
        String request = StudentSearch.normalize(input.request());
        LessonReportContent current = input.currentContent();
        if (request.contains("aprovar") || request.contains("approves")) {
            return output(current, current.observations(), true, "APPROVE");
        }
        if (request.contains("cancelar") || request.contains("discards")) {
            return output(current, current.observations(), true, "CANCEL");
        }
        return output(current, input.request().strip(), false, "NONE");
    }

    private static AiLessonReportOutput output(
            LessonReportContent base,
            String observations,
            boolean withoutChange,
            String actionRequest) {
        return new AiLessonReportOutput(
                base.workedTopics(),
                base.progress(),
                base.difficulties(),
                base.proposedActivities(),
                observations,
                List.of(),
                withoutChange,
                actionRequest);
    }

    private static String join(String current, String increase) {
        return current == null || current.isBlank()
                ? increase.strip()
                : current + " " + increase.strip();
    }
}
