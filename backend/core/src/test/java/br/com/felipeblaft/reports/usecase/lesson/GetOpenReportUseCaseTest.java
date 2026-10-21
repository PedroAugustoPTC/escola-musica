package br.com.felipeblaft.reports.usecase.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetOpenReportUseCaseTest {

    private final ReportScenario c = new ReportScenario();

    @Test
    void withoutOpenReportReturnsEmpty() {
        assertThat(c.getOpen.run(c.clara.id(), c.matteo.id())).isEmpty();
    }

    @Test
    void returnsReportWithChannelAndLastChange() {
        OpenReport openReport =
                new OpenReport(
                        ReportType.LESSON,
                        UUID.randomUUID(),
                        null,
                        c.matteo.id(),
                        "Matteo Mafra",
                        ReportStatus.AWAITING_REVIEW,
                        null,
                        null,
                        Channel.TELEGRAM,
                        Instant.parse("2026-09-20T12:57:00Z"));
        c.open.add(c.clara.id(), openReport);

        assertThat(c.getOpen.run(c.clara.id(), c.matteo.id())).contains(openReport);
    }

    @Test
    void otherTeachersStudentIsNotRevealed() {
        assertThatThrownBy(() -> c.getOpen.run(c.clara.id(), c.helena.id()))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
