package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Custo e canal de cada chamada de IA gravada pelo processador (FR-051, FR-052). */
class JobProcessorCostTest {

    private final ReportScenario c = new ReportScenario();

    private ProcessingJob process(Channel channel) {
        ProcessingJob job =
                c.register.run(
                        c.clara.id(),
                        c.matteo.id(),
                        ReportScenario.file("lesson.ogg", 10),
                        channel);
        c.processQueue();
        return c.queue.searchById(job.id()).orElseThrow();
    }

    @Test
    @DisplayName("cada chamada gravada leva o canal do pedido, nos dois canais")
    void everyCallCarriesTheRequestChannel() {
        for (Channel channel : Channel.values()) {
            c.costs.calls.clear();
            c.transcription.schedule("Today we worked on the scales.");
            c.llm.schedule(
                    TaskLlm.LESSON_STRUCTURING, ReportScenario.output("Improved.", List.of()));

            process(channel);

            assertThat(c.costs.calls).isNotEmpty();
            assertThat(c.costs.calls).extracting(AiCall::channel).containsOnly(channel);
        }
    }

    @Test
    @DisplayName("o custo gravado é o estimado pelo provedor na hora da chamada e não muda depois")
    void recordedCostIsTheEstimateOfTheCall() {
        c.transcription.schedule("Today we worked on the scales.");
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("Improved.", List.of()));

        process(Channel.WEB);

        AiCall transcription =
                c.costs.calls.stream()
                        .filter(call -> call.task() == AiTask.TRANSCRIPTION)
                        .findFirst()
                        .orElseThrow();
        AiCall structuring =
                c.costs.calls.stream()
                        .filter(call -> call.task() == AiTask.STRUCTURING)
                        .findFirst()
                        .orElseThrow();
        assertThat(transcription.estimatedCostUsd()).isEqualByComparingTo(new BigDecimal("0.001"));
        assertThat(structuring.estimatedCostUsd()).isEqualByComparingTo(new BigDecimal("0.0001"));
        assertThat(transcription.success()).isTrue();
    }

    @Test
    @DisplayName("a chamada que falha também leva provedor e canal, para o alerta por provedor")
    void failedCallCarriesProviderAndChannel() {
        c.transcription.schedule(
                new ProviderUnavailableException("down"), new ProviderUnavailableException("down"));

        process(Channel.TELEGRAM);

        AiCall failed =
                c.costs.calls.stream().filter(call -> !call.success()).findFirst().orElseThrow();
        assertThat(failed.channel()).isEqualTo(Channel.TELEGRAM);
        assertThat(failed.modelProvider()).isEqualTo("provider/model");
        assertThat(failed.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }
}
