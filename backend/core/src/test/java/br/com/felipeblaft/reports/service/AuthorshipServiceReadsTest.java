package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.doubles.InMemoryApprovedReportQuery;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase;
import br.com.felipeblaft.reports.usecase.home.GetStudentSummaryUseCase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eventos que o professor gera ao decidir e ler relatórios (FR-044): aprovar, cancelar, consultar o
 * histórico e abrir, baixar ou receber um PDF. O sucesso entra na mesma transação; sem o evento, a
 * operação ou a leitura não vale.
 */
class AuthorshipServiceReadsTest {

    private final ReportScenario c = new ReportScenario();
    private final QueryStudentHistoryUseCase history =
            new QueryStudentHistoryUseCase(
                    new GetStudentSummaryUseCase(c.students, c.open, c.authorization),
                    new InMemoryApprovedReportQuery(c.reports, c.semester, c.students, c.teachers),
                    c.reportAudit);

    private List<AuthorshipRecord> events(String operation) {
        return c.authorshipRecorder.ofOperation(operation);
    }

    @Test
    @DisplayName("aprovar grava LESSON_REPORT_APPROVE com a versão aprovada, na mesma transação")
    void approvingRecordsTheEventWithTheApprovedVersion() {
        LessonReport report = c.reportInReview();

        c.approve.run(c.clara.id(), report.id(), 1, Channel.WEB);

        AuthorshipRecord event = events("LESSON_REPORT_APPROVE").getFirst();
        assertThat(c.authorshipRecorder.inTransaction()).contains(event);
        assertThat(event.authorId()).isEqualTo(c.clara.id());
        assertThat(event.authorRole()).isEqualTo(Role.TEACHER);
        assertThat(event.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.LESSON_REPORT);
        assertThat(event.targetId()).isEqualTo(report.id());
        assertThat(event.readDetail().documentVersion()).isEqualTo(1);
        assertThat(event.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(event.correlationId()).isEqualTo("req-test");
    }

    @Test
    @DisplayName("no Telegram o evento leva o canal do Telegram e o update como correlação")
    void telegramEventsCarryTheChannelAndTheUpdate() {
        c.requestContext.define(ActionOrigin.TELEGRAM, "98765");
        LessonReport report = c.reportInReview();

        c.cancel.cancel(c.clara.id(), report.id());

        AuthorshipRecord event = events("LESSON_REPORT_CANCEL").getFirst();
        assertThat(event.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(event.correlationId()).isEqualTo("98765");
        assertThat(event.readDetail()).isNull();
    }

    @Test
    @DisplayName("aprovação recusada não grava evento de sucesso")
    void refusedApprovalRecordsNothing() {
        LessonReport report = c.reportInReview();

        assertThatThrownBy(() -> c.approve.run(c.clara.id(), report.id(), 99, Channel.WEB))
                .isInstanceOf(RuntimeException.class);

        assertThat(events("LESSON_REPORT_APPROVE")).isEmpty();
    }

    @Test
    @DisplayName("sem gravar o evento a aprovação não vale: a exceção sobe")
    void approvalFailsWhenTheEventCannotBeRecorded() {
        LessonReport report = c.reportInReview();
        c.authorshipRecorder.failNextRecordWith(new IllegalStateException("sem gravação"));

        assertThatThrownBy(() -> c.approve.run(c.clara.id(), report.id(), 1, Channel.WEB))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("histórico consultado vira HISTORY_QUERY sobre o aluno")
    void historyQueryIsRecorded() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");

        history.run(c.clara.id(), c.matteo.id());

        AuthorshipRecord event = events("HISTORY_QUERY").getFirst();
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
        assertThat(event.targetId()).isEqualTo(c.matteo.id());
        assertThat(event.readDetail().pageNumber()).isZero();
    }

    @Test
    @DisplayName("PDF oficial: aberto e baixado na Web, enviado no Telegram, sempre com a versão")
    void officialPdfReadsAreRecordedPerChannel() {
        LessonReport report = c.reportInReview();
        c.approve.run(c.clara.id(), report.id(), 1, Channel.WEB);

        c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id(), false);
        c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id(), true);
        c.requestContext.define(ActionOrigin.TELEGRAM, "55");
        c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id());

        assertThat(events("REPORT_DOCUMENT_OPEN")).hasSize(1);
        assertThat(events("REPORT_DOCUMENT_DOWNLOAD")).hasSize(1);
        assertThat(events("REPORT_DOCUMENT_SENT")).hasSize(1);
        assertThat(events("REPORT_DOCUMENT_OPEN").getFirst().readDetail().documentVersion())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("sem o evento, o PDF não é entregue")
    void pdfIsNotDeliveredWithoutTheEvent() {
        LessonReport report = c.reportInReview();
        c.approve.run(c.clara.id(), report.id(), 1, Channel.WEB);
        c.authorshipRecorder.failNextRecordWith(new IllegalStateException("sem gravação"));

        assertThatThrownBy(
                        () -> c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("entrega automática do PDF pelo trabalho de fundo não é leitura do professor")
    void backgroundDeliveryRecordsNothing() {
        LessonReport report = c.reportInReview();
        c.approve.run(c.clara.id(), report.id(), 1, Channel.WEB);
        c.requestContext.deactivate();

        c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id());

        assertThat(events("REPORT_DOCUMENT_OPEN")).isEmpty();
        assertThat(events("REPORT_DOCUMENT_SENT")).isEmpty();
    }

    @Test
    @DisplayName("professor alheio não gera evento de leitura: a recusa vem antes")
    void anotherTeacherRecordsNoReadEvent() {
        LessonReport report = c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");

        assertThatThrownBy(
                        () -> c.getDocument.official(c.bruno.id(), ReportType.LESSON, report.id()))
                .isInstanceOf(RuntimeException.class);

        assertThat(events("REPORT_DOCUMENT_OPEN")).isEmpty();
    }
}
