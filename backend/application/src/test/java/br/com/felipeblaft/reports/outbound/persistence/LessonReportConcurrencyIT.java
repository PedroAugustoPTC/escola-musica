package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Aprovação concorrente pelos dois canais e unicidade do relatório em aberto (FR-034, FR-033). */
class LessonReportConcurrencyIT extends IntegrationBase {

    @Autowired private ScenarioIT scenario;
    @Autowired private RegisterLessonByAudioUseCase register;
    @Autowired private AnswerQuestionsUseCase answer;
    @Autowired private ApproveLessonReportUseCase approve;
    @Autowired private LessonReportRepository reports;

    @Autowired private br.com.felipeblaft.reports.config.ApplicationProperties properties;

    private Teacher clara;
    private Student matteo;
    private UUID reportId;

    @BeforeEach
    void reachReview() throws Exception {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        Path directory = properties.audio().tempDir();
        Files.createDirectories(directory);
        Path file = directory.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(file, AudioNormalizationTest.AUDIO_WITH_SPEECH);
        register.run(
                clara.id(), matteo.id(), new TemporaryFile(file, 22, "audio/webm"), Channel.WEB);
        scenario.processQueue();
        reportId = jdbc.queryForObject("select id from lesson_report", UUID.class);
        answer.answerText(clara.id(), reportId, "Estudou bem.", Channel.WEB);
        scenario.processQueue();
    }

    @Test
    void simultaneousApprovalByBothChannelsOnlyOneWins() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService channels = Executors.newFixedThreadPool(2);
        List<Future<String>> results = new ArrayList<>();
        for (Channel channel : List.of(Channel.WEB, Channel.TELEGRAM)) {
            Callable<String> attempt =
                    () -> {
                        start.await();
                        try {
                            approve.run(clara.id(), reportId, 1, channel);
                            return "APROVADO";
                        } catch (DomainException refuses) {
                            return refuses.code().name();
                        } catch (ConcurrencyConflictException conflict) {
                            return "CONFLITO";
                        }
                    };
            results.add(channels.submit(attempt));
        }
        start.countDown();
        List<String> codes = new ArrayList<>();
        for (Future<String> result : results) {
            codes.add(result.get(60, TimeUnit.SECONDS));
        }
        channels.shutdown();

        assertThat(codes).containsOnlyOnce("APROVADO");
        assertThat(codes)
                .filteredOn(c -> !c.equals("APROVADO"))
                .singleElement()
                .isIn(ErrorCode.RELATORIO_JA_APROVADO.name(), "CONFLITO");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pdf_document where type = 'OFICIAL'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from lesson_report", String.class))
                .isEqualTo("APROVADO");
    }

    @Test
    void secondApprovalAfterFirstIsRefused() {
        approve.run(clara.id(), reportId, 1, Channel.WEB);

        assertThatThrownBy(() -> approve.run(clara.id(), reportId, 1, Channel.TELEGRAM))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RELATORIO_JA_APROVADO));
    }

    @Test
    void partialIndexPreventsSecondOpenReportForSameStudent() {
        Lesson lesson =
                Lesson.create(clara.id(), matteo.id(), clock.todayInSchoolTimezone(), clock.now());
        reports.saveLesson(lesson);
        LessonReport other =
                LessonReport.create(lesson, "transcrição", Channel.TELEGRAM, clock.now());

        assertThatThrownBy(() -> reports.save(other))
                .isInstanceOf(ConcurrencyConflictException.class);
    }
}
