package br.com.felipeblaft.reports.domain.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validação do filtro da visão da escola (US3). */
class SchoolHistoryFilterTest {

    private static final LocalDate MARCH_10 = LocalDate.of(2026, 3, 10);

    /**
     * O mesmo caminho da produção: forma na borda (parseSyntax), ordem do período no construtor.
     */
    private static SchoolHistoryFilter parse(
            UUID studentId,
            UUID teacherId,
            String type,
            String from,
            String to,
            Integer page,
            Integer size) {
        SchoolHistoryFilter.Syntax syntax =
                SchoolHistoryFilter.parseSyntax(studentId, teacherId, type, from, to, page, size);
        return new SchoolHistoryFilter(
                syntax.studentId(),
                syntax.teacherId(),
                syntax.type(),
                syntax.from(),
                syntax.to(),
                syntax.page());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> fieldsOf(Throwable error) {
        DomainException domain = (DomainException) error;
        assertThat(domain.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        return (List<Map<String, String>>) domain.details().get("fields");
    }

    @Test
    void periodStartAfterEndIsRefusedOnFieldFrom() {
        assertThatThrownBy(
                        () ->
                                new SchoolHistoryFilter(
                                        null,
                                        null,
                                        null,
                                        MARCH_10.plusDays(1),
                                        MARCH_10,
                                        PageRequest.first()))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e ->
                                assertThat(fieldsOf(e))
                                        .containsExactly(
                                                Map.of(
                                                        "field",
                                                        "from",
                                                        "code",
                                                        "PERIOD_START_AFTER_END")));
    }

    @Test
    void equalDatesAreAccepted() {
        assertThatCode(
                        () ->
                                new SchoolHistoryFilter(
                                        null, null, null, MARCH_10, MARCH_10, PageRequest.first()))
                .doesNotThrowAnyException();
    }

    @Test
    void typeIsOptionalAndAllFiltersProduceCriteria() {
        UUID student = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();

        SchoolHistoryFilter open = parse(null, null, null, null, null, null, null);
        SchoolHistoryFilter full =
                parse(student, teacher, "SEMESTER", "2026-01-01", "2026-06-30", 2, 50);

        assertThat(open.criteria()).isEqualTo(ApprovedReportCriteria.unfiltered());
        assertThat(open.page()).isEqualTo(PageRequest.first());
        assertThat(full.criteria())
                .isEqualTo(
                        new ApprovedReportCriteria(
                                student,
                                teacher,
                                ReportType.SEMESTER,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 6, 30)));
        assertThat(full.page()).isEqualTo(new PageRequest(2, 50));
    }

    @Test
    void blankValuesCountAsAbsent() {
        SchoolHistoryFilter filter = parse(null, null, " ", "", "  ", null, null);

        assertThat(filter.criteria()).isEqualTo(ApprovedReportCriteria.unfiltered());
    }

    @Test
    void malformedDatesAndUnknownTypeAreReportedTogetherWithoutTheValue() {
        assertThatThrownBy(
                        () -> parse(null, null, "ANNUAL", "10/03/2026", "2026-02-30", null, null))
                .satisfies(
                        e -> {
                            assertThat(fieldsOf(e))
                                    .containsExactlyInAnyOrder(
                                            Map.of("field", "from", "code", "INVALID_DATE"),
                                            Map.of("field", "to", "code", "INVALID_DATE"),
                                            Map.of("field", "type", "code", "INVALID_REPORT_TYPE"));
                            assertThat(fieldsOf(e).toString())
                                    .doesNotContain("ANNUAL", "10/03/2026");
                        });
    }

    @Test
    void parseRefusesPeriodStartAfterEnd() {
        assertThatThrownBy(() -> parse(null, null, null, "2026-05-02", "2026-05-01", null, null))
                .satisfies(
                        e ->
                                assertThat(fieldsOf(e))
                                        .containsExactly(
                                                Map.of(
                                                        "field",
                                                        "from",
                                                        "code",
                                                        "PERIOD_START_AFTER_END")));
    }

    @Test
    void pageAndSizeFollowThePageRequestLimits() {
        assertThatThrownBy(() -> parse(null, null, null, null, null, -1, 101))
                .satisfies(
                        e ->
                                assertThat(fieldsOf(e))
                                        .extracting(field -> field.get("field"))
                                        .containsExactly("page", "size"));
        assertThatCode(() -> parse(null, null, null, null, null, 0, 100))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> parse(null, null, null, null, null, 0, 0))
                .satisfies(
                        e ->
                                assertThat(fieldsOf(e))
                                        .extracting(field -> field.get("field"))
                                        .containsExactly("size"));
    }
}
