package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeOpenReportsQueryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final FakeOpenReportsQuery query = new FakeOpenReportsQuery();
    private final UUID clara = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID matteo = UUID.randomUUID();
    private final UUID olavo = UUID.randomUUID();

    private OpenReport report(UUID studentId, ReportType type) {
        return new OpenReport(
                type,
                UUID.randomUUID(),
                null,
                studentId,
                "Aluno",
                ReportStatus.AWAITING_REVIEW,
                null,
                null,
                Channel.WEB,
                NOW);
    }

    @Test
    void countsTheTeachersOpenReportsOfTheStudent() {
        query.add(clara, report(matteo, ReportType.LESSON));
        query.add(clara, report(matteo, ReportType.SEMESTER));
        query.add(clara, report(olavo, ReportType.LESSON));

        assertThat(query.countOpenReports(clara, matteo)).isEqualTo(2);
        assertThat(query.countOpenReports(clara, olavo)).isEqualTo(1);
    }

    @Test
    void doesNotCountAnotherTeachersReports() {
        query.add(bruno, report(matteo, ReportType.LESSON));

        assertThat(query.countOpenReports(clara, matteo)).isZero();
        assertThat(query.countOpenReports(bruno, matteo)).isEqualTo(1);
    }

    @Test
    void unknownTeacherOrStudentCountsZero() {
        assertThat(query.countOpenReports(UUID.randomUUID(), UUID.randomUUID())).isZero();
    }
}
