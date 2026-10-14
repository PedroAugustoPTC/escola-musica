package br.com.felipeblaft.reports.usecase.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.TemporaryFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegisterLessonByAudioUseCaseTest {

    private final ReportScenario c = new ReportScenario();

    @Test
    void enqueuesRegistrationWithChannelAndFile() {
        TemporaryFile file = ReportScenario.file("web.webm", 2048);

        ProcessingJob job = c.register.run(c.clara.id(), c.matteo.id(), file, Channel.WEB);

        assertThat(job.type()).isEqualTo(JobType.REGISTRO_AULA);
        assertThat(job.requestChannel()).isEqualTo(Channel.WEB);
        assertThat(job.step()).isEqualTo(JobStep.ENVIANDO);
        assertThat(job.temporaryFile()).isEqualTo(Path.of("/tmp/audios/web.webm").toString());
        assertThat(job.parameters()).containsEntry("fileSize", "2048");
        assertThat(c.queue.jobs).containsKey(job.id());
    }

    @Test
    void telegramReferenceDoesNotBringBytes() {
        ChannelFileReference voice = new ChannelFileReference("file-1", "u-1", "audio/ogg", 30_000);

        ProcessingJob job = c.register.run(c.clara.id(), c.matteo.id(), voice, Channel.TELEGRAM);

        assertThat(job.temporaryFile()).isNull();
        assertThat(job.parameters())
                .containsEntry("channelFile", "file-1")
                .containsEntry("uniqueChannelFile", "u-1")
                .containsEntry("fileMime", "audio/ogg");
    }

    @Test
    void otherTeachersStudentDiscardsAudio() {
        TemporaryFile file = ReportScenario.file("x.ogg", 10);
        assertThatThrownBy(() -> c.register.run(c.clara.id(), c.helena.id(), file, Channel.WEB))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.ALUNO_NAO_ASSOCIADO);
        assertThat(c.normalization.deleted).contains(file.path());
        assertThat(c.queue.jobs).isEmpty();
    }

    @Test
    void openReportWithDetails() {
        Instant when = Instant.parse("2026-09-20T12:57:00Z");
        c.open.add(
                c.clara.id(),
                new OpenReport(
                        ReportType.AULA,
                        null,
                        java.util.UUID.randomUUID(),
                        c.matteo.id(),
                        "Matteo Mafra",
                        null,
                        JobStep.TRANSCREVENDO,
                        null,
                        Channel.TELEGRAM,
                        when));

        assertThatThrownBy(
                        () ->
                                c.register.run(
                                        c.clara.id(),
                                        c.matteo.id(),
                                        ReportScenario.file("x", 1),
                                        Channel.WEB))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e -> {
                            DomainException error = (DomainException) e;
                            assertThat(error.code()).isEqualTo(ErrorCode.RELATORIO_EM_ABERTO);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> openReport =
                                    (Map<String, Object>) error.details().get("relatorioEmAberto");
                            assertThat(openReport)
                                    .containsEntry("canalOrigem", Channel.TELEGRAM)
                                    .containsEntry("atualizadoEm", when);
                        });
    }

    @Test
    void registrationStillInTranscriptionCountsAsOpen() {
        c.register.run(c.clara.id(), c.matteo.id(), ReportScenario.file("a", 1), Channel.WEB);
        assertThatThrownBy(
                        () ->
                                c.register.run(
                                        c.clara.id(),
                                        c.matteo.id(),
                                        ReportScenario.file("b", 1),
                                        Channel.TELEGRAM))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.RELATORIO_EM_ABERTO);
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/b"));
    }

    @Test
    void audioTooLargeReportsLimit() {
        long above = c.parameters.audioLimitBytes() + 1;
        assertThatThrownBy(
                        () ->
                                c.register.run(
                                        c.clara.id(),
                                        c.matteo.id(),
                                        new ChannelFileReference("f", null, "video/mp4", above),
                                        Channel.TELEGRAM))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e -> {
                            assertThat(((DomainException) e).code())
                                    .isEqualTo(ErrorCode.AUDIO_MUITO_GRANDE);
                            assertThat(((DomainException) e).details())
                                    .containsEntry("limiteMb", 50);
                        });
        assertThat(c.queue.jobs).isEmpty();
    }

    @Test
    void lessonDateNearMidnightInSchoolTimezone() {
        // 02:30 UTC de 22/09 ainda é 21/09 em São Paulo
        c.clock.define(Instant.parse("2026-09-22T02:30:00Z"));
        c.llm.schedule(
                br.com.felipeblaft.reports.port.TaskLlm.ESTRUTURACAO_AULA,
                ReportScenario.output("x", List.of()));
        c.register.run(c.clara.id(), c.matteo.id(), ReportScenario.file("a", 1), Channel.WEB);
        c.processQueue();
        assertThat(c.reports.lessons.values())
                .singleElement()
                .satisfies(
                        a ->
                                assertThat(a.lessonDate())
                                        .isEqualTo(java.time.LocalDate.of(2026, 9, 21)));
    }
}
