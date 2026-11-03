package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SC-002 no Telegram: o professor cadastra um aluno adulto em até 3 minutos e em poucas interações.
 * O simulador responde com 3 segundos simulados por mensagem (o relógio ajustável avança a cada
 * interação); o cadastro falha se passar de 180 segundos simulados ou de 8 interações. A pilha é a
 * real, com Postgres real e o cadastro realmente gravado.
 */
class StudentRegistryTelegramTimingIT extends TelegramConversationBase {

    private static final Duration SIMULATED_RESPONSE = Duration.ofSeconds(3);
    private static final Duration LIMIT = Duration.ofSeconds(180);
    private static final int MAX_INTERACTIONS = 8;

    private int interactions;
    private Instant start;

    @BeforeEach
    void link() throws Exception {
        scenario.clara();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        interactions = 0;
        start = clock.now();
    }

    /** Uma interação do professor: envia o texto e espera a resposta simulada de 3 s. */
    private String send(String text) throws Exception {
        API_BOTS.resetRequests();
        sendText(USER_CLARA, text);
        interactions++;
        clock.advance(SIMULATED_RESPONSE);
        return lastMessage().path("text").asString();
    }

    @Test
    void adultRegistrationFinishesWithinThreeMinutesAndEightInteractions() throws Exception {
        assertThat(send("cadastrar aluno")).isEqualTo("Qual é o nome do aluno?");
        assertThat(send("Ana Souza")).startsWith("Qual é a data de nascimento?");
        assertThat(send("05/03/1990")).startsWith("Qual é o CPF do aluno?");
        assertThat(send(CanaryCpf.MASKED)).startsWith("Qual é o instrumento?");
        assertThat(send("pular")).startsWith("Confira o cadastro:");
        assertThat(send("sim")).startsWith("Pronto! Ana Souza foi cadastrado(a)");

        Duration elapsed = Duration.between(start, clock.now());
        assertThat(interactions).as("interações").isLessThanOrEqualTo(MAX_INTERACTIONS);
        assertThat(elapsed).as("tempo simulado").isLessThanOrEqualTo(LIMIT);
        assertThat(elapsed).isEqualTo(SIMULATED_RESPONSE.multipliedBy(interactions));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student where name = 'Ana Souza'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from teacher_student_assignment", Integer.class))
                .isEqualTo(1);
    }
}
