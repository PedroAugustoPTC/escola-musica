package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LogCaptureTest {

    private static final String APPLICATION_LOGGER = "br.com.felipeblaft";
    private static final org.slf4j.Logger LOG =
            LoggerFactory.getLogger("br.com.felipeblaft.support.LogCaptureTest");
    private static final org.slf4j.Logger OUTSIDE = LoggerFactory.getLogger("org.example.Outside");

    @Test
    void capturesTheMessagesOfTheApplicationAtAnyLevelUpToDebug() {
        try (LogCapture capture = LogCapture.start()) {
            LOG.debug("passo {}", 1);
            LOG.info("passo {}", 2);
            LOG.error("passo {}", 3);

            assertThat(capture.messages()).contains("passo 1", "passo 2", "passo 3");
        }
    }

    @Test
    void capturesOtherLoggersFromInfoOn() {
        try (LogCapture capture = LogCapture.start()) {
            OUTSIDE.debug("detail");
            OUTSIDE.info("aviso");

            assertThat(capture.messages()).contains("aviso").doesNotContain("detail");
        }
    }

    @Test
    void textCarriesMessageMdcAndTheWholeExceptionTrace() {
        try (LogCapture capture = LogCapture.start()) {
            MDC.put("correlationId", "req-42");
            try {
                LOG.error("falha ao gravar", new IllegalStateException("causa-especifica"));
            } finally {
                MDC.remove("correlationId");
            }

            assertThat(capture.text())
                    .contains("falha ao gravar")
                    .contains("correlationId")
                    .contains("req-42")
                    .contains("IllegalStateException")
                    .contains("causa-especifica")
                    .contains("LogCaptureTest");
        }
    }

    @Test
    void textIncludesTheCausesOfTheException() {
        try (LogCapture capture = LogCapture.start()) {
            LOG.error(
                    "erro externo",
                    new RuntimeException("externa", new IllegalArgumentException("inner-cause")));

            assertThat(capture.text()).contains("externa").contains("inner-cause");
        }
    }

    @Test
    void clearForgetsWhatWasCaptured() {
        try (LogCapture capture = LogCapture.start()) {
            LOG.info("before");
            capture.clear();
            LOG.info("after");

            assertThat(capture.messages()).contains("after").doesNotContain("before");
        }
    }

    @Test
    void closeRestoresTheLevelsAndStopsCapturing() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger application = (Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        Level rootBefore = root.getLevel();
        Level applicationBefore = application.getLevel();

        LogCapture capture = LogCapture.start();
        LOG.info("during");
        capture.close();
        LOG.info("depois de fechar");

        assertThat(root.getLevel()).isEqualTo(rootBefore);
        assertThat(application.getLevel()).isEqualTo(applicationBefore);
        assertThat(capture.messages()).contains("during").doesNotContain("depois de fechar");
    }

    @Test
    void closeTwiceIsHarmless() {
        LogCapture capture = LogCapture.start();

        capture.close();

        assertThatCode(capture::close).doesNotThrowAnyException();
    }

    @Test
    void sweeperFindsACanaryThatReachedTheLog() {
        ResponseSweeper sweeper = ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf());
        try (LogCapture capture = LogCapture.start()) {
            LOG.warn("cadastro recusado para {}", CanaryStudent.NAME);

            assertThatThrownBy(() -> sweeper.assertClean(Place.LOG, capture.text()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("nome do aluno canário");
        }
    }

    @Test
    void sweeperFindsACanaryHiddenInTheExceptionTrace() {
        ResponseSweeper sweeper = ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf());
        try (LogCapture capture = LogCapture.start()) {
            LOG.error("failure", new IllegalStateException("CPF " + CanaryCpf.MASKED));

            assertThat(sweeper.sweepText(Place.LOG, capture.text()))
                    .extracting(ResponseSweeper.Finding::label)
                    .containsExactly("CPF canário");
        }
    }

    @Test
    void cleanLogHasNoFinding() {
        ResponseSweeper sweeper = ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf());
        try (LogCapture capture = LogCapture.start()) {
            LOG.info("aluno {} cadastrado", "6f0d7a4e");

            sweeper.assertClean(Place.LOG, capture.text());
        }
    }
}
