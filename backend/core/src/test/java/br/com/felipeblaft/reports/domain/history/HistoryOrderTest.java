package br.com.felipeblaft.reports.domain.history;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-007: ordem total e estável do histórico, igual para professor e administrador. */
class HistoryOrderTest {

    private static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    private static ApprovedReportRow lesson(String id, LocalDate date, Instant approvedAt) {
        return new ApprovedReportRow(
                ReportType.LESSON,
                UUID.fromString(id),
                UUID.randomUUID(),
                "Student",
                true,
                UUID.randomUUID(),
                "Teacher",
                true,
                date,
                null,
                approvedAt,
                1);
    }

    private static ApprovedReportRow semester(
            String id, SemesterPeriod period, Instant approvedAt) {
        return new ApprovedReportRow(
                ReportType.SEMESTER,
                UUID.fromString(id),
                UUID.randomUUID(),
                "Student",
                true,
                UUID.randomUUID(),
                "Teacher",
                true,
                null,
                period,
                approvedAt,
                1);
    }

    private static String id(int n) {
        return "00000000-0000-0000-0000-%012d".formatted(n);
    }

    private static List<ApprovedReportRow> sorted(List<ApprovedReportRow> rows) {
        return rows.stream().sorted(HistoryOrder.NEWEST_FIRST).toList();
    }

    @Test
    void newestReferenceDateComesFirst() {
        ApprovedReportRow older = lesson(id(1), LocalDate.of(2026, 3, 1), T0.plusSeconds(500));
        ApprovedReportRow newer = lesson(id(2), LocalDate.of(2026, 4, 1), T0);

        assertThat(sorted(List.of(older, newer))).containsExactly(newer, older);
    }

    @Test
    void sameReferenceDateOrdersByApprovalDescending() {
        LocalDate day = LocalDate.of(2026, 5, 10);
        ApprovedReportRow early = lesson(id(1), day, T0);
        ApprovedReportRow late = lesson(id(2), day, T0.plusSeconds(60));

        assertThat(sorted(List.of(early, late))).containsExactly(late, early);
    }

    @Test
    void sameDateAndApprovalOrdersByReportIdDescending() {
        LocalDate day = LocalDate.of(2026, 5, 10);
        ApprovedReportRow low = lesson(id(1), day, T0);
        ApprovedReportRow high = lesson(id(2), day, T0);

        assertThat(sorted(List.of(low, high))).containsExactly(high, low);
    }

    @Test
    void lessonUsesTheLessonDateAndSemesterUsesTheStartOfThePeriod() {
        SemesterPeriod first = new SemesterPeriod(2026, 1);
        ApprovedReportRow semesterRow = semester(id(1), first, T0);
        ApprovedReportRow lessonRow = lesson(id(2), LocalDate.of(2026, 2, 15), T0);

        assertThat(semesterRow.reference()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(lessonRow.reference()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(sorted(List.of(semesterRow, lessonRow)))
                .containsExactly(lessonRow, semesterRow);
    }

    @Test
    void tieBetweenLessonAndSemesterOnTheSameDateFallsBackToApproval() {
        SemesterPeriod second = new SemesterPeriod(2026, 2);
        ApprovedReportRow semesterRow = semester(id(1), second, T0.plusSeconds(10));
        ApprovedReportRow lessonRow = lesson(id(2), LocalDate.of(2026, 7, 1), T0);

        assertThat(sorted(List.of(lessonRow, semesterRow)))
                .containsExactly(semesterRow, lessonRow);
    }

    @Test
    void orderIsTotalAndIndependentOfTheInputOrder() {
        List<ApprovedReportRow> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            LocalDate day = LocalDate.of(2026, 1 + (i % 3), 1);
            Instant approvedAt = T0.plusSeconds(i % 4);
            rows.add(
                    i % 5 == 0
                            ? semester(id(i), new SemesterPeriod(2026, 1), approvedAt)
                            : lesson(id(i), day, approvedAt));
        }
        List<ApprovedReportRow> expected = sorted(rows);

        for (long seed = 0; seed < 5; seed++) {
            List<ApprovedReportRow> shuffled = new ArrayList<>(rows);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(sorted(shuffled)).isEqualTo(expected);
        }
        assertThat(HistoryOrder.NEWEST_FIRST.compare(expected.get(0), expected.get(0))).isZero();
    }

    @Test
    void rowToStringDoesNotRevealNames() {
        ApprovedReportRow row = lesson(id(1), LocalDate.of(2026, 5, 10), T0);

        assertThat(row.toString()).doesNotContain("Student").doesNotContain("Teacher");
    }
}
