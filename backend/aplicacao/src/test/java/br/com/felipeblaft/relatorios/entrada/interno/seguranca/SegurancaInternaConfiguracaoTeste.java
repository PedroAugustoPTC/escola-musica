package br.com.felipeblaft.relatorios.entrada.interno.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao.CanalTelegram;
import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao.EntregaProativa;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** FR-026: sem nenhum token a subida avisa (sem imprimir valor algum); com token, não avisa. */
class SegurancaInternaConfiguracaoTeste {

    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void capturar() {
        logger = (Logger) LoggerFactory.getLogger(SegurancaInternaConfiguracao.class);
        eventos.start();
        logger.addAppender(eventos);
    }

    @AfterEach
    void liberar() {
        logger.detachAppender(eventos);
        eventos.stop();
    }

    private static CanalTelegram canal(String atual, String proximo) {
        return new CanalTelegram(
                EntregaProativa.DIRETA,
                atual,
                proximo,
                "",
                "",
                3000,
                10000,
                30,
                900,
                24,
                60,
                60,
                2000,
                20,
                24,
                7);
    }

    @Test
    void semNenhumTokenEmiteUmAvisoSemValores() {
        SegurancaInternaConfiguracao.avisarSeNaoHaToken(canal("", null));
        SegurancaInternaConfiguracao.avisarSeNaoHaToken(canal("  ", ""));

        assertThat(eventos.list).hasSize(2);
        assertThat(eventos.list)
                .allSatisfy(
                        evento -> {
                            assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                            assertThat(evento.getFormattedMessage())
                                    .contains("canal-telegram-sem-token-de-servico")
                                    .contains("CANAL_TELEGRAM_TOKEN_ATUAL");
                        });
    }

    @Test
    void comAlgumTokenNaoAvisaENuncaImprimeOToken() {
        SegurancaInternaConfiguracao.avisarSeNaoHaToken(canal("tok-secreto-1", null));
        SegurancaInternaConfiguracao.avisarSeNaoHaToken(canal("", "tok-secreto-2"));
        assertThat(eventos.list).isEmpty();
    }
}
