package br.com.felipeblaft.reports.domain.semester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PeriodoSemestral, InformacoesGerais e RelatorioSemestral (data-model.md §9). */
class SemesterDomainTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private static void code(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    @Test
    void periodHasKeyDatesAndOrder() {
        SemesterPeriod first = SemesterPeriod.of("2026-1");
        SemesterPeriod second = SemesterPeriod.containing(LocalDate.of(2026, 9, 21));

        assertThat(first.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(first.end()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(second.key()).isEqualTo("2026-2");
        assertThat(second.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(second.end()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(second.previous()).isEqualTo(first);
        assertThat(first.previous().key()).isEqualTo("2025-2");
        assertThat(second.inProgress(LocalDate.of(2026, 9, 21))).isTrue();
        assertThat(first.inProgress(LocalDate.of(2026, 9, 21))).isFalse();
        assertThat(first.contains(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(first.contains(LocalDate.of(2026, 7, 1))).isFalse();
    }

    @Test
    void invalidKeyAndInvalidData() {
        for (String key : new String[] {"2026-3", "26-1", "2026/1", "", null}) {
            code(() -> SemesterPeriod.of(key), ErrorCode.DADOS_INVALIDOS);
        }
    }

    @Test
    void availableFromNewestToOldest() {
        List<SemesterPeriod> periods =
                SemesterPeriod.available(LocalDate.of(2025, 3, 10), LocalDate.of(2026, 9, 21));

        assertThat(periods)
                .extracting(SemesterPeriod::key)
                .containsExactly("2026-2", "2026-1", "2025-2", "2025-1");
        assertThat(SemesterPeriod.available(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 9, 21)))
                .extracting(SemesterPeriod::key)
                .containsExactly("2026-2");
    }

    @Test
    void ageAtPeriodEndOrToday() {
        SemesterPeriod past = SemesterPeriod.of("2025-1");
        SemesterPeriod current = SemesterPeriod.of("2026-2");
        LocalDate birth = LocalDate.of(2012, 5, 10);
        LocalDate today = LocalDate.of(2026, 3, 1);

        GeneralInformation inPast =
                GeneralInformation.calculate("Matteo", birth, "Piano", "Clara", past, 4, today);
        GeneralInformation inCurrent =
                GeneralInformation.calculate("Matteo", birth, "Piano", "Clara", current, 2, today);

        assertThat(inPast.idade()).isEqualTo(13);
        assertThat(inCurrent.idade()).isEqualTo(13);
        assertThat(
                        GeneralInformation.calculate(
                                        "Matteo",
                                        birth,
                                        null,
                                        "Clara",
                                        current,
                                        2,
                                        LocalDate.of(2026, 9, 21))
                                .idade())
                .isEqualTo(14);
        assertThat(inPast.periodoChave()).isEqualTo("2025-1");
        assertThat(inPast.quantidadeAulasConsideradas()).isEqualTo(4);
    }

    @Test
    void contentNormalizesMissingSectionsAndBlankItems() {
        SemesterReportContent content =
                new SemesterReportContent(
                        null,
                        new SemesterReportContent.Technique(
                                List.of(" Escalas ", " "), null, null, null),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "  Parecer  ");

        assertThat(content.tecnica().conteudosTrabalhados()).containsExactly("Escalas");
        assertThat(content.frequenciaEEstudo().comentarioGeral()).isEmpty();
        assertThat(content.pontosDeAtencao().itens()).isEmpty();
        assertThat(content.parecerFinal()).isEqualTo("Parecer");
        assertThat(SemesterReportContent.empty()).isEqualTo(SemesterReportContent.empty());
    }

    private static SemesterReport create() {
        SemesterPeriod period = SemesterPeriod.of("2026-1");
        return SemesterReport.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                period,
                3,
                1,
                GeneralInformation.calculate(
                        "Matteo",
                        LocalDate.of(2012, 5, 10),
                        "Piano",
                        "Clara",
                        period,
                        3,
                        LocalDate.of(2026, 9, 21)),
                SemesterReportContent.empty(),
                Channel.WEB,
                NOW);
    }

    @Test
    void startsInReviewAtVersion1AndChangeGeneratesNewVersion() {
        SemesterReport report = create();
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_CONFERENCIA);
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(report.openReportCount()).isEqualTo(1);

        report.requireCanChange(1);
        report.startStep(ReportStep.APLICANDO_ALTERACAO, NOW);
        code(() -> report.requireCanChange(1), ErrorCode.PROCESSAMENTO_EM_ANDAMENTO);
        assertThat(report.applyChange(SemesterReportContent.empty(), NOW)).isEqualTo(2);
        code(() -> report.requireCanChange(1), ErrorCode.VERSAO_DESATUALIZADA);
        code(() -> report.approve(1, Channel.WEB, NOW), ErrorCode.VERSAO_DESATUALIZADA);
    }

    @Test
    void approvedOrCancelledAcceptsNothingMore() {
        SemesterReport approved = create();
        approved.approve(1, Channel.TELEGRAM, NOW);
        assertThat(approved.approvedVersion()).isEqualTo(1);
        assertThat(approved.approvalChannel()).isEqualTo(Channel.TELEGRAM);
        code(() -> approved.approve(1, Channel.WEB, NOW), ErrorCode.RELATORIO_JA_APROVADO);
        code(() -> approved.cancel(NOW), ErrorCode.RELATORIO_JA_APROVADO);

        SemesterReport cancelled = create();
        cancelled.cancel(NOW);
        code(() -> cancelled.requireCanChange(1), ErrorCode.RELATORIO_CANCELADO);
        assertThatThrownBy(() -> cancelled.applyChange(SemesterReportContent.empty(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
