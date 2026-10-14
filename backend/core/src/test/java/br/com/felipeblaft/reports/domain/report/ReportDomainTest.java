package br.com.felipeblaft.reports.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ConteudoRelatorioAula, VersaoRelatorio, InteracaoRelatorio e DocumentoPdf. */
class ReportDomainTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");

    @Test
    void contentNormalizesWithoutInventing() {
        LessonReportContent content =
                new LessonReportContent(
                        Arrays.asList(" Czerny ", "", null, "  "),
                        null,
                        null,
                        List.of("Escala"),
                        "  obs ");
        assertThat(content.workedTopics()).containsExactly("Czerny");
        assertThat(content.progress()).isEmpty();
        assertThat(content.difficulties()).isEmpty();
        assertThat(content.observations()).isEqualTo("obs");
        assertThat(content.isEmpty()).isFalse();
        assertThat(LessonReportContent.empty().isEmpty()).isTrue();
        assertThat(new LessonReportContent(List.of(), "x", List.of(), List.of(), "").isEmpty())
                .isFalse();
        assertThat(new LessonReportContent(List.of(), "", List.of("d"), List.of(), "").isEmpty())
                .isFalse();
        assertThat(new LessonReportContent(List.of(), "", List.of(), List.of("a"), "").isEmpty())
                .isFalse();
        assertThat(new LessonReportContent(List.of(), "", List.of(), List.of(), "o").isEmpty())
                .isFalse();
    }

    @Test
    void immutableVersionStartsAtOne() {
        UUID report = UUID.randomUUID();
        ReportVersion version =
                ReportVersion.create(
                        ReportType.LESSON,
                        report,
                        1,
                        LessonReportContent.empty(),
                        VersionOrigin.INITIAL_STRUCTURING,
                        null,
                        Channel.WEB,
                        "lesson-structuring.v1",
                        T0);
        assertThat(version.number()).isEqualTo(1);
        assertThat(version.reportId()).isEqualTo(report);
        assertThatThrownBy(
                        () ->
                                ReportVersion.create(
                                        ReportType.LESSON,
                                        report,
                                        0,
                                        LessonReportContent.empty(),
                                        VersionOrigin.CHANGE,
                                        null,
                                        Channel.WEB,
                                        null,
                                        T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interactionDoesNotExposeText() {
        ReportInteraction interaction =
                ReportInteraction.create(
                        ReportType.LESSON,
                        UUID.randomUUID(),
                        InteractionType.CHANGE,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        "troque a evolução",
                        2,
                        null,
                        InteractionResult.APPLIED,
                        3,
                        T0);
        assertThat(interaction.toString()).doesNotContain("swap").contains("CHANGE");
        assertThat(interaction.resultingVersion()).isEqualTo(3);
    }

    @Test
    void pdfDocumentAndLesson() {
        PdfDocument document =
                PdfDocument.create(
                        UUID.randomUUID(),
                        DocumentType.OFFICIAL,
                        "reports/lesson/x/official.pdf",
                        10,
                        "sha",
                        T0);
        assertThat(document.type()).isEqualTo(DocumentType.OFFICIAL);
        assertThatThrownBy(
                        () ->
                                new Lesson(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        null,
                                        T0))
                .isInstanceOf(NullPointerException.class);
        assertThat(ReportStep.valueOf("GENERATING_PDF")).isNotNull();
        assertThat(VersionOrigin.values()).hasSize(4);
    }
}
