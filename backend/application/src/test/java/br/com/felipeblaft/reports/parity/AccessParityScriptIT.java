package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Paridade de acesso, início, busca, bloqueio e encerramento (US2; quickstart V1, V2, V15). */
class AccessParityScriptIT extends TelegramConversationBase {

    @Autowired MessageCatalog catalog;

    @BeforeEach
    void load() {
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        scenario.student(clara, "Matteo Mafra", "Piano");
        scenario.student(clara, "Olavo Torrente", "Violão");
        scenario.student(clara, "Miguel de Castro Torrente", "Violino");
        scenario.student(bruno, "Helena Prado", "Flauta");
    }

    private TelegramChannelDriver telegram() {
        return new TelegramChannelDriver(
                text -> {
                    API_BOTS.resetRequests();
                    sendText(USER_CLARA, text);
                    return calls("sendMessage");
                },
                catalog,
                () -> jdbc.queryForObject("select count(*) from telegram_link", Long.class));
    }

    private static ParityScript script(Runnable lockEnd) {
        return new ParityScript()
                .step("código errado", c -> c.signIn("ZZZZZZZZ"))
                .step("código errado 2", c -> c.signIn("ZZZZZZZZ"))
                .step("código errado 3", c -> c.signIn("ZZZZZZZZ"))
                .step("código errado 4", c -> c.signIn("ZZZZZZZZ"))
                .step("código errado 5", c -> c.signIn("ZZZZZZZZ"))
                .step("código certo bloqueado", c -> c.signIn(ScenarioIT.CODE_CLARA))
                .step(
                        "fim do bloqueio",
                        c -> {
                            lockEnd.run();
                            return c.signIn(ScenarioIT.CODE_CLARA);
                        })
                .step("início", ChannelDriver::start)
                .step("busca com um", c -> c.search("matteo"))
                .step("busca com vários", c -> c.search("torrente"))
                .step("busca sem resultado", c -> c.search("Helena"))
                .step("encerrar", ChannelDriver::end);
    }

    @Test
    void sameScriptSameResultsInBothChannels() throws Exception {
        List<String> web =
                script(() -> clock.advance(Duration.ofMinutes(15)))
                        .run(new ChannelWebDriver(mvc, "198.51.100.20"));
        clock.define(br.com.felipeblaft.reports.support.AdjustableClock.DEFAULT);
        List<String> bot = script(() -> clock.advance(Duration.ofMinutes(15))).run(telegram());

        assertThat(bot).containsExactlyElementsOf(web);
        assertThat(web)
                .contains(
                        "código errado = INVALID_CODE",
                        "código certo bloqueado = TEMPORARILY_BLOCKED",
                        "fim do bloqueio = SUCCESS",
                        "início = HomeSummary[totalStudents=3, students=[Matteo Mafra, Miguel de Castro Torrente, Olavo Torrente]]",
                        "busca com um = SearchResult[type=FOUND, students=[Matteo Mafra]]",
                        "busca com vários = SearchResult[type=FOUND, students=[Miguel de Castro Torrente, Olavo Torrente]]",
                        "busca sem resultado = SearchResult[type=NONE, students=[Matteo Mafra, Miguel de Castro Torrente, Olavo Torrente]]",
                        "encerrar = ENDED");
    }
}
