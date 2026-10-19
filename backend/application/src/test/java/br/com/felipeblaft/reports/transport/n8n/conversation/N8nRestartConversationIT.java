package br.com.felipeblaft.reports.transport.n8n.conversation;

import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.restartN8n;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O estado da conversa vive só no backend (feature 004, FR-016, US6 cenário 4): com o professor no
 * meio do registro de aula, reiniciar o n8n não perde a etapa, e a conversa continua do mesmo
 * ponto.
 */
class N8nRestartConversationIT extends TelegramConversationBase {

    @BeforeEach
    void prepare() throws Exception {
        Assumptions.assumeTrue("n8n".equals(TRANSPORT));
        var clara = scenario.clara();
        scenario.student(clara, "Matteo Ferreira", "Piano");
        scenario.student(clara, "Helena Prado", "Violino");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    @Test
    @DisplayName(
            "a conversa no meio do registro de aula continua da mesma etapa depois do reinício do n8n")
    void conversationContinuesFromTheSameStepAfterRestart() throws Exception {
        sendCallback(USER_CLARA, "v1:register:");
        assertThat(lastMessage().get("text").asString()).isEqualTo("Para qual aluno?");

        restartN8n();
        awaitHealthy(Duration.ofSeconds(180));
        sendCallback(USER_CLARA, "v1:student:1");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Aula de hoje, 21/09/2026, com Matteo Ferreira. Envie o áudio da aula (mensagem de voz ou arquivo, até 50 MB).");
    }
}
