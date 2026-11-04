package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Detalhe das leituras da visão da escola (data-model.md §4): só identificadores e números. */
class AuthorshipReadDetailTest {

    @Test
    void queryDetailKeepsFiltersAndPage() {
        UUID teacherId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        AuthorshipReadDetail detail =
                AuthorshipReadDetail.ofQuery(teacherId, ReportType.SEMESTER, from, to, 3);

        assertThat(detail.filterTeacherId()).isEqualTo(teacherId);
        assertThat(detail.filterReportType()).isEqualTo(ReportType.SEMESTER);
        assertThat(detail.filterPeriodStart()).isEqualTo(from);
        assertThat(detail.filterPeriodEnd()).isEqualTo(to);
        assertThat(detail.pageNumber()).isEqualTo(3);
        assertThat(detail.documentVersion()).isNull();
    }

    @Test
    void documentDetailKeepsOnlyTheVersion() {
        AuthorshipReadDetail detail = AuthorshipReadDetail.ofDocument(3);

        assertThat(detail.documentVersion()).isEqualTo(3);
        assertThat(detail.pageNumber()).isNull();
        assertThat(detail.filterTeacherId()).isNull();
        assertThat(detail.filterReportType()).isNull();
    }

    @Test
    void everyFilterIsOptional() {
        assertThatCode(() -> AuthorshipReadDetail.ofQuery(null, null, null, null, 0))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsBothReportTypeFilters() {
        for (ReportType type : ReportType.values()) {
            assertThatCode(() -> AuthorshipReadDetail.ofQuery(null, type, null, null, 0))
                    .as(type.name())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void pageNumberCannotBeNegative() {
        assertThatThrownBy(() -> AuthorshipReadDetail.ofQuery(null, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> AuthorshipReadDetail.ofQuery(null, null, null, null, 0))
                .doesNotThrowAnyException();
    }

    @Test
    void documentVersionStartsAtOne() {
        assertThatThrownBy(() -> AuthorshipReadDetail.ofDocument(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorshipReadDetail.ofDocument(-2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> AuthorshipReadDetail.ofDocument(1)).doesNotThrowAnyException();
    }

    @Test
    void toStringNeverRevealsTheValues() {
        UUID teacherId = UUID.randomUUID();
        AuthorshipReadDetail detail =
                AuthorshipReadDetail.ofQuery(
                        teacherId, ReportType.LESSON, LocalDate.of(2026, 3, 9), null, 7);

        assertThat(detail.toString())
                .doesNotContain(teacherId.toString())
                .doesNotContain("2026")
                .doesNotContain("LESSON");
    }
}
