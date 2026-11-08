package br.com.felipeblaft.reports.usecase.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.InMemoryApprovedReportQuery;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase.Command;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase.Result;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Visão da escola: histórico de um aluno, de todos os professores (US1). */
class QuerySchoolHistoryUseCaseTest {

    private static final String CORRELATION = "req-1";

    private final ReportScenario c = new ReportScenario();
    private final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    private final InMemoryApprovedReportQuery approved =
            new InMemoryApprovedReportQuery(c.reports, c.semester, c.students, c.teachers);
    private final QuerySchoolHistoryUseCase history =
            new QuerySchoolHistoryUseCase(
                    new ActorAuthorizationService(PermissionMatrix.standard()),
                    c.students,
                    approved,
                    new AuthorshipService(recorder, c.clock),
                    c.transaction);
    private final Actor admin = ActorFixtures.administratorWeb();

    private static Command forStudent(UUID studentId) {
        return new Command(studentId, null, null, null, null, PageRequest.first(), CORRELATION);
    }

    private ApprovedReportRow lessonOf(
            UUID teacherId, boolean teacherActive, LocalDate date, int version) {
        return new ApprovedReportRow(
                ReportType.LESSON,
                UUID.randomUUID(),
                c.matteo.id(),
                c.matteo.name(),
                true,
                teacherId,
                "Professor",
                teacherActive,
                date,
                null,
                c.clock.now(),
                version);
    }

    private ApprovedReportRow semesterOf(UUID teacherId, SemesterPeriod period) {
        return new ApprovedReportRow(
                ReportType.SEMESTER,
                UUID.randomUUID(),
                c.matteo.id(),
                c.matteo.name(),
                true,
                teacherId,
                "Professor",
                true,
                null,
                period,
                c.clock.now(),
                1);
    }

    @Test
    void administratorSeesApprovedOfAllTeachersNewestFirstWithApprovedVersion() {
        var fromClara = c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        ApprovedReportRow fromInactiveTeacher =
                lessonOf(c.bruno.id(), false, LocalDate.of(2026, 5, 20), 3);
        approved.add(fromInactiveTeacher);
        c.reportWithQuestions(); // não aprovado: não entra

        Result result = history.run(admin, forStudent(c.matteo.id()));

        assertThat(result.student().id()).isEqualTo(c.matteo.id());
        assertThat(result.page().total()).isEqualTo(2);
        assertThat(result.page().items())
                .extracting(ApprovedReportRow::reportId)
                .containsExactly(fromInactiveTeacher.reportId(), fromClara.id());
        assertThat(result.page().items().getFirst().approvedVersion()).isEqualTo(3);
        assertThat(result.page().items().getFirst().teacherActive()).isFalse();
    }

    @Test
    void twoSemesterReportsOfTheSamePeriodAreSeparateItems() {
        SemesterPeriod period = SemesterPeriod.of("2026-1");
        approved.add(semesterOf(c.clara.id(), period));
        approved.add(semesterOf(c.bruno.id(), period));

        Result result = history.run(admin, forStudent(c.matteo.id()));

        assertThat(result.page().items()).hasSize(2);
        assertThat(result.page().items())
                .extracting(ApprovedReportRow::teacherId)
                .containsExactlyInAnyOrder(c.clara.id(), c.bruno.id());
    }

    @Test
    void inactiveStudentIsStillQueryable() {
        c.students.searchById(c.matteo.id()).orElseThrow().inactivate(c.clock.now());
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");

        Result result = history.run(admin, forStudent(c.matteo.id()));

        assertThat(result.student().active()).isFalse();
        assertThat(result.page().items()).hasSize(1);
    }

    @Test
    void studentWithoutApprovedReturnsEmptyPageWithSummary() {
        Result result = history.run(admin, forStudent(c.matteo.id()));

        assertThat(result.page().items()).isEmpty();
        assertThat(result.page().total()).isZero();
        assertThat(result.student().name()).isEqualTo("Matteo Mafra");
    }

    @Test
    void unknownStudentReturnsEmptyPageWithoutSummary() {
        Result result = history.run(admin, forStudent(UUID.randomUUID()));

        assertThat(result.page().items()).isEmpty();
        assertThat(result.student()).isNull();
    }

    @Test
    void teacherOnAnyChannelGetsResourceNotFoundAndNothingIsRecorded() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");

        for (Actor teacher :
                new Actor[] {
                    ActorFixtures.teacherWeb(c.clara.id()),
                    ActorFixtures.teacherTelegram(c.clara.id())
                }) {
            assertThatThrownBy(() -> history.run(teacher, forStudent(c.matteo.id())))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
        assertThat(recorder.isEmpty()).isTrue();
    }

    @Test
    void provisionalPasswordIsRefused() {
        assertThatThrownBy(
                        () ->
                                history.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        forStudent(c.matteo.id())))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThat(recorder.isEmpty()).isTrue();
    }

    @Test
    void recordsOneSuccessInTheSameTransactionWithStudentTargetAndPageNumber() {
        for (int day = 1; day <= 3; day++) {
            c.clock.advance(Duration.ofMinutes(1));
            c.approvedLesson(c.matteo, LocalDate.of(2026, 3, day), "Aula " + day);
        }

        history.run(
                admin,
                new Command(
                        c.matteo.id(), null, null, null, null, new PageRequest(1, 2), CORRELATION));

        assertThat(recorder.records()).hasSize(1);
        AuthorshipRecord record = recorder.inTransaction().getFirst();
        assertThat(record.operation()).isEqualTo(Operation.SCHOOL_HISTORY_QUERY.name());
        assertThat(record.authorId()).isEqualTo(ActorFixtures.ADMINISTRATOR_ID);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
        assertThat(record.targetId()).isEqualTo(c.matteo.id());
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.readDetail().pageNumber()).isEqualTo(1);
        assertThat(record.readDetail().filterTeacherId()).isNull();
    }

    @Test
    void withoutStudentFilterTheTargetIsNull() {
        history.run(
                admin, new Command(null, null, null, null, null, PageRequest.first(), CORRELATION));

        AuthorshipRecord record = recorder.records().getFirst();
        assertThat(record.targetType()).isNull();
        assertThat(record.targetId()).isNull();
    }

    @Test
    void failureToRecordDeliversNothing() {
        recorder.failNextRecordWith(new IllegalStateException("falha simulada"));

        assertThatThrownBy(() -> history.run(admin, forStudent(c.matteo.id())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neverWritesOnReports() {
        var report = c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        int versionBefore = report.approvedVersion();
        int reportsBefore = c.reports.reports.size();
        int recordingsBefore = c.reports.recordings;

        history.run(admin, forStudent(c.matteo.id()));

        assertThat(c.reports.reports).hasSize(reportsBefore);
        assertThat(c.reports.recordings).isEqualTo(recordingsBefore);
        assertThat(c.reports.searchById(report.id()).orElseThrow().approvedVersion())
                .isEqualTo(versionBefore);
    }

    private static Command filtered(
            UUID teacherId, ReportType type, LocalDate from, LocalDate to, PageRequest page) {
        return new Command(null, teacherId, type, from, to, page, CORRELATION);
    }

    @Test
    void teacherOnlyFilterListsAllHisStudentsWithStudentName() {
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 10), 1));
        approved.add(lessonOf(c.bruno.id(), true, LocalDate.of(2026, 3, 12), 1));

        Result result =
                history.run(admin, filtered(c.clara.id(), null, null, null, PageRequest.first()));

        assertThat(result.page().total()).isEqualTo(1);
        assertThat(result.page().items().getFirst().teacherId()).isEqualTo(c.clara.id());
        assertThat(result.page().items().getFirst().studentName()).isEqualTo(c.matteo.name());
    }

    @Test
    void typeOnlyFilterKeepsOneType() {
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 10), 1));
        approved.add(semesterOf(c.clara.id(), SemesterPeriod.of("2026-1")));

        Result result =
                history.run(
                        admin,
                        filtered(null, ReportType.SEMESTER, null, null, PageRequest.first()));

        assertThat(result.page().items())
                .extracting(ApprovedReportRow::type)
                .containsExactly(ReportType.SEMESTER);
    }

    @Test
    void periodOnlyFilterUsesReferenceDateInclusive() {
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 9), 1));
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 10), 1));
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 20), 1));
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 21), 1));

        Result result =
                history.run(
                        admin,
                        filtered(
                                null,
                                null,
                                LocalDate.of(2026, 3, 10),
                                LocalDate.of(2026, 3, 20),
                                PageRequest.first()));

        assertThat(result.page().items())
                .extracting(ApprovedReportRow::lessonDate)
                .containsExactly(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 10));
    }

    @Test
    void allFiltersCombinedAreRecordedWithPageNumber() {
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 10), 1));
        approved.add(lessonOf(c.bruno.id(), true, LocalDate.of(2026, 3, 10), 1));
        approved.add(semesterOf(c.clara.id(), SemesterPeriod.of("2026-1")));

        Result result =
                history.run(
                        admin,
                        new Command(
                                c.matteo.id(),
                                c.clara.id(),
                                ReportType.LESSON,
                                LocalDate.of(2026, 3, 1),
                                LocalDate.of(2026, 3, 31),
                                PageRequest.first(),
                                CORRELATION));

        assertThat(result.page().items()).hasSize(1);
        AuthorshipRecord record = recorder.inTransaction().getFirst();
        assertThat(record.readDetail().filterTeacherId()).isEqualTo(c.clara.id());
        assertThat(record.readDetail().filterReportType()).isEqualTo(ReportType.LESSON);
        assertThat(record.readDetail().filterPeriodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(record.readDetail().filterPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(record.readDetail().pageNumber()).isZero();
    }

    @Test
    void unknownTeacherReturnsEmptyPage() {
        approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, 10), 1));

        Result result =
                history.run(
                        admin, filtered(UUID.randomUUID(), null, null, null, PageRequest.first()));

        assertThat(result.page().items()).isEmpty();
        assertThat(result.page().total()).isZero();
    }

    @Test
    void paginationKeepsTotalAndOrder() {
        for (int day = 1; day <= 5; day++) {
            approved.add(lessonOf(c.clara.id(), true, LocalDate.of(2026, 3, day), 1));
        }

        Result result = history.run(admin, filtered(null, null, null, null, new PageRequest(1, 2)));

        assertThat(result.page().total()).isEqualTo(5);
        assertThat(result.page().items())
                .extracting(ApprovedReportRow::lessonDate)
                .containsExactly(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 2));
    }

    @Test
    void periodStartAfterEndRecordsFailureIndependentlyAndRethrows() {
        assertThatThrownBy(
                        () ->
                                history.run(
                                        admin,
                                        new Command(
                                                c.matteo.id(),
                                                c.clara.id(),
                                                ReportType.LESSON,
                                                LocalDate.of(2026, 5, 2),
                                                LocalDate.of(2026, 5, 1),
                                                PageRequest.first(),
                                                CORRELATION)))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThat(recorder.inTransaction()).isEmpty();
        AuthorshipRecord record = recorder.independent().getFirst();
        assertThat(record.operation()).isEqualTo(Operation.SCHOOL_HISTORY_QUERY.name());
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(record.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
        assertThat(record.targetId()).isEqualTo(c.matteo.id());
        assertThat(record.readDetail().filterTeacherId()).isEqualTo(c.clara.id());
    }

    @Test
    void resultToStringDoesNotRevealNames() {
        Result result = history.run(admin, forStudent(c.matteo.id()));

        assertThat(result.toString()).doesNotContain("Matteo");
        assertThat(result.student().toString()).doesNotContain("Matteo");
    }
}
