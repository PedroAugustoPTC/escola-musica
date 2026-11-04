package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportCriteria;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.history.HistoryOrder;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.port.ApprovedReportQueryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Consulta única de relatórios aprovados com Postgres real (feature 006, R-01): só aprovados, ordem
 * do SQL igual à de {@link HistoryOrder}, filtros pela data de referência, paginação com total e
 * dados de aluno e professor (inclusive inativos e professor desassociado).
 */
class ApprovedReportQueryAdapterIT extends RegistryPersistenceBase {

    private static final Instant BASE = Instant.parse("2026-08-01T12:00:00Z");
    private static final List<String> NOT_APPROVED =
            List.of(
                    "PROCESSING",
                    "PROCESSING_FAILED",
                    "AWAITING_ANSWERS",
                    "AWAITING_REVIEW",
                    "CANCELLED");

    @Autowired ApprovedReportQueryPort query;

    // ---- semeadura direta no banco, para controlar estado, datas e instantes de aprovação

    private UUID lessonReport(
            Teacher teacher,
            Student student,
            LocalDate lessonDate,
            String status,
            Instant approvedAt,
            int approvedVersion) {
        UUID lessonId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        boolean approved = "APPROVED".equals(status);
        jdbc.update(
                "insert into lesson (id, teacher_id, student_id, lesson_date, created_at)"
                        + " values (?, ?, ?, ?, ?)",
                lessonId,
                teacher.id(),
                student.id(),
                lessonDate,
                Timestamp.from(BASE));
        jdbc.update(
                "insert into lesson_report (id, lesson_id, teacher_id, student_id, transcription,"
                        + " content, pending_questions, question_rounds, status, current_version,"
                        + " origin_channel, created_at, updated_at, approved_at, approved_version,"
                        + " approval_channel, processing_time_ms, lock_version) values (?, ?, ?, ?,"
                        + " 'texto', '{}'::jsonb, '[]'::jsonb, 0, ?, ?, 'WEB', ?, ?, ?, ?, ?, 0, 0)",
                reportId,
                lessonId,
                teacher.id(),
                student.id(),
                status,
                approved ? approvedVersion : 1,
                Timestamp.from(BASE),
                Timestamp.from(BASE),
                approved ? Timestamp.from(approvedAt) : null,
                approved ? approvedVersion : null,
                approved ? "WEB" : null);
        return reportId;
    }

    private UUID semesterReport(
            Teacher teacher,
            Student student,
            String periodKey,
            String status,
            Instant approvedAt,
            int approvedVersion) {
        SemesterPeriod period = SemesterPeriod.of(periodKey);
        UUID reportId = UUID.randomUUID();
        boolean approved = "APPROVED".equals(status);
        jdbc.update(
                "insert into semester_report (id, teacher_id, student_id, period_key,"
                        + " period_start, period_end, considered_lesson_count, open_report_count,"
                        + " general_information, content, status, current_version, origin_channel,"
                        + " created_at, updated_at, approved_at, approved_version, approval_channel,"
                        + " processing_time_ms, lock_version) values (?, ?, ?, ?, ?, ?, 1, 0,"
                        + " '{}'::jsonb, '{}'::jsonb, ?, ?, 'WEB', ?, ?, ?, ?, ?, 0, 0)",
                reportId,
                teacher.id(),
                student.id(),
                periodKey,
                period.start(),
                period.end(),
                status,
                approved ? approvedVersion : 1,
                Timestamp.from(BASE),
                Timestamp.from(BASE),
                approved ? Timestamp.from(approvedAt) : null,
                approved ? approvedVersion : null,
                approved ? "WEB" : null);
        return reportId;
    }

    private static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private static ApprovedReportCriteria criteria(
            Student student, Teacher teacher, ReportType type, LocalDate from, LocalDate to) {
        return new ApprovedReportCriteria(
                student == null ? null : student.id(),
                teacher == null ? null : teacher.id(),
                type,
                from,
                to);
    }

    private List<UUID> ids(ApprovedReportCriteria criteria) {
        return query.all(criteria).stream().map(ApprovedReportRow::reportId).toList();
    }

    // ---- (a) nenhum relatório não aprovado aparece

    @Test
    void neverReturnsAReportThatIsNotApproved() {
        Teacher clara = teacher("Clara Souza", true);
        Set<UUID> approved = new HashSet<>();
        int count = 0;
        for (String status : NOT_APPROVED) {
            Student lessonStudent = student("Aluno Aula " + letter(count));
            Student semesterStudent = student("Aluno Semestre " + letter(count));
            lessonReport(clara, lessonStudent, LocalDate.of(2026, 5, 10), status, null, 1);
            semesterReport(clara, semesterStudent, "2026-1", status, null, 1);
            count++;
        }
        Student matteo = student("Matteo Ferreira");
        approved.add(lessonReport(clara, matteo, LocalDate.of(2026, 5, 11), "APPROVED", BASE, 1));
        approved.add(semesterReport(clara, matteo, "2026-1", "APPROVED", BASE, 2));

        assertThat(ids(ApprovedReportCriteria.unfiltered()))
                .containsExactlyInAnyOrderElementsOf(approved);
        PageResult<ApprovedReportRow> page =
                query.page(ApprovedReportCriteria.unfiltered(), new PageRequest(0, 20));
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
    }

    // ---- (b) a ordem do SQL é a de HistoryOrder, inclusive nos empates

    @Test
    void sqlOrderEqualsHistoryOrderWithTiesOnDateAndApproval() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        List<Teacher> teacherList = List.of(clara, bruno);
        List<Student> studentList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            studentList.add(student("Aluno " + letter(i)));
        }
        Random random = new Random(42);
        List<LocalDate> dates =
                List.of(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 15),
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 8, 20));
        List<String> keys = List.of("2026-1", "2026-2", "2025-2", "2025-1");
        List<ApprovedReportRow> expected = new ArrayList<>();
        Set<String> usedSemesters = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            Teacher teacher = teacherList.get(random.nextInt(2));
            Student student = studentList.get(random.nextInt(4));
            Instant approvedAt = BASE.plusSeconds(random.nextInt(3));
            if (random.nextInt(3) == 0) {
                String key = keys.get(random.nextInt(keys.size()));
                if (!usedSemesters.add(teacher.id() + "|" + student.id() + "|" + key)) {
                    continue;
                }
                UUID id = semesterReport(teacher, student, key, "APPROVED", approvedAt, 1);
                expected.add(
                        new ApprovedReportRow(
                                ReportType.SEMESTER,
                                id,
                                student.id(),
                                student.name(),
                                true,
                                teacher.id(),
                                teacher.name(),
                                true,
                                null,
                                SemesterPeriod.of(key),
                                approvedAt,
                                1));
            } else {
                LocalDate date = dates.get(random.nextInt(dates.size()));
                UUID id = lessonReport(teacher, student, date, "APPROVED", approvedAt, 1);
                expected.add(
                        new ApprovedReportRow(
                                ReportType.LESSON,
                                id,
                                student.id(),
                                student.name(),
                                true,
                                teacher.id(),
                                teacher.name(),
                                true,
                                date,
                                null,
                                approvedAt,
                                1));
            }
        }
        List<UUID> expectedOrder =
                expected.stream()
                        .sorted(HistoryOrder.NEWEST_FIRST)
                        .map(ApprovedReportRow::reportId)
                        .toList();

        assertThat(ids(ApprovedReportCriteria.unfiltered()))
                .containsExactlyElementsOf(expectedOrder);
        List<UUID> paged = new ArrayList<>();
        for (int page = 0; page * 7 < expectedOrder.size(); page++) {
            query.page(ApprovedReportCriteria.unfiltered(), new PageRequest(page, 7))
                    .items()
                    .forEach(row -> paged.add(row.reportId()));
        }
        assertThat(paged).containsExactlyElementsOf(expectedOrder);
    }

    // ---- (c) filtros, sozinhos e combinados

    @Test
    void eachFilterAloneAndCombined() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student matteo = student("Matteo Ferreira");
        Student sofia = student("Sofia Alves");
        UUID claraMatteoMarch =
                lessonReport(clara, matteo, LocalDate.of(2026, 3, 10), "APPROVED", BASE, 1);
        UUID brunoMatteoMay =
                lessonReport(bruno, matteo, LocalDate.of(2026, 5, 20), "APPROVED", BASE, 1);
        UUID claraSofiaJune =
                lessonReport(clara, sofia, LocalDate.of(2026, 6, 30), "APPROVED", BASE, 1);
        UUID claraMatteoFirstSemester =
                semesterReport(clara, matteo, "2026-1", "APPROVED", BASE, 1);
        UUID brunoSofiaSecondSemester = semesterReport(bruno, sofia, "2026-2", "APPROVED", BASE, 1);

        assertThat(ids(criteria(matteo, null, null, null, null)))
                .containsExactlyInAnyOrder(
                        claraMatteoMarch, brunoMatteoMay, claraMatteoFirstSemester);
        assertThat(ids(criteria(null, bruno, null, null, null)))
                .containsExactlyInAnyOrder(brunoMatteoMay, brunoSofiaSecondSemester);
        assertThat(ids(criteria(null, null, ReportType.LESSON, null, null)))
                .containsExactlyInAnyOrder(claraMatteoMarch, brunoMatteoMay, claraSofiaJune);
        assertThat(ids(criteria(null, null, ReportType.SEMESTER, null, null)))
                .containsExactlyInAnyOrder(claraMatteoFirstSemester, brunoSofiaSecondSemester);
        assertThat(ids(criteria(matteo, clara, null, null, null)))
                .containsExactlyInAnyOrder(claraMatteoMarch, claraMatteoFirstSemester);
        assertThat(ids(criteria(matteo, bruno, ReportType.LESSON, null, null)))
                .containsExactly(brunoMatteoMay);
        assertThat(ids(criteria(sofia, bruno, ReportType.LESSON, null, null))).isEmpty();
        assertThat(
                        ids(
                                criteria(
                                        null,
                                        clara,
                                        ReportType.LESSON,
                                        LocalDate.of(2026, 4, 1),
                                        LocalDate.of(2026, 12, 31))))
                .containsExactly(claraSofiaJune);
    }

    @Test
    void periodFilterUsesTheReferenceDateWithInclusiveBounds() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = student("Matteo Ferreira");
        UUID lessonOnFirst =
                lessonReport(clara, matteo, LocalDate.of(2026, 3, 1), "APPROVED", BASE, 1);
        UUID lessonOnLast =
                lessonReport(clara, matteo, LocalDate.of(2026, 3, 31), "APPROVED", BASE, 1);
        UUID lessonOutside =
                lessonReport(clara, matteo, LocalDate.of(2026, 4, 1), "APPROVED", BASE, 1);
        UUID semesterStartingBefore = semesterReport(clara, matteo, "2026-1", "APPROVED", BASE, 1);
        UUID semesterStartingInside = semesterReport(clara, matteo, "2026-2", "APPROVED", BASE, 1);

        assertThat(
                        ids(
                                criteria(
                                        null,
                                        null,
                                        null,
                                        LocalDate.of(2026, 3, 1),
                                        LocalDate.of(2026, 3, 31))))
                .containsExactlyInAnyOrder(lessonOnFirst, lessonOnLast);
        // o semestral 2026-1 (01/01 a 30/06) começa antes do intervalo: fica de fora
        assertThat(ids(criteria(null, null, null, LocalDate.of(2026, 3, 1), null)))
                .doesNotContain(semesterStartingBefore)
                .contains(semesterStartingInside, lessonOutside);
        // o semestral 2026-2 começa em 01/07: entra com limite inclusivo nos dois lados
        assertThat(
                        ids(
                                criteria(
                                        null,
                                        null,
                                        ReportType.SEMESTER,
                                        LocalDate.of(2026, 7, 1),
                                        LocalDate.of(2026, 7, 1))))
                .containsExactly(semesterStartingInside);
        assertThat(ids(criteria(null, null, null, null, LocalDate.of(2026, 2, 28))))
                .containsExactly(semesterStartingBefore);
        assertThat(
                        query.page(
                                        criteria(null, null, null, LocalDate.of(2026, 3, 1), null),
                                        new PageRequest(0, 2))
                                .total())
                .isEqualTo(4);
    }

    // ---- (d) página e total

    @Test
    void pageSliceAndTotalFollowTheSameCriteria() {
        Teacher clara = teacher("Clara Souza", true);
        Student matteo = student("Matteo Ferreira");
        for (int day = 1; day <= 25; day++) {
            lessonReport(clara, matteo, LocalDate.of(2026, 5, day), "APPROVED", BASE, 1);
        }
        lessonReport(clara, student("Sofia Alves"), LocalDate.of(2026, 5, 1), "APPROVED", BASE, 1);
        ApprovedReportCriteria forMatteo = criteria(matteo, null, null, null, null);

        PageResult<ApprovedReportRow> first = query.page(forMatteo, new PageRequest(0, 10));
        PageResult<ApprovedReportRow> last = query.page(forMatteo, new PageRequest(2, 10));
        PageResult<ApprovedReportRow> beyond = query.page(forMatteo, new PageRequest(3, 10));

        assertThat(first.items()).hasSize(10);
        assertThat(first.total()).isEqualTo(25);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(10);
        assertThat(first.items().getFirst().lessonDate()).isEqualTo(LocalDate.of(2026, 5, 25));
        assertThat(last.items()).hasSize(5);
        assertThat(last.items().getLast().lessonDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(25);
        assertThat(query.all(forMatteo)).hasSize(25);
    }

    // ---- (e) nome e situação de aluno e professor, versão aprovada

    @Test
    void carriesNamesStatusAndApprovedVersionEvenForInactiveOrUnassignedPeople() {
        Teacher active = teacher("Clara Souza", true);
        Teacher inactive = teacher("Bruno Lima", true);
        Student inactiveStudent = student("Matteo Ferreira");
        // nenhum dos dois tem associação: o histórico da escola não depende dela
        UUID lessonId =
                lessonReport(
                        active, inactiveStudent, LocalDate.of(2026, 5, 20), "APPROVED", BASE, 3);
        UUID semesterId = semesterReport(inactive, inactiveStudent, "2026-1", "APPROVED", BASE, 2);
        inactivate(inactive);
        inactiveStudent.inactivate(now());
        students.save(inactiveStudent);

        List<ApprovedReportRow> rows = query.all(criteria(inactiveStudent, null, null, null, null));

        ApprovedReportRow lesson =
                rows.stream().filter(r -> r.reportId().equals(lessonId)).findFirst().orElseThrow();
        assertThat(lesson.type()).isEqualTo(ReportType.LESSON);
        assertThat(lesson.studentName()).isEqualTo("Matteo Ferreira");
        assertThat(lesson.studentActive()).isFalse();
        assertThat(lesson.teacherId()).isEqualTo(active.id());
        assertThat(lesson.teacherName()).isEqualTo("Clara Souza");
        assertThat(lesson.teacherActive()).isTrue();
        assertThat(lesson.lessonDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(lesson.period()).isNull();
        assertThat(lesson.approvedAt()).isEqualTo(BASE);
        assertThat(lesson.approvedVersion()).isEqualTo(3);
        ApprovedReportRow semester =
                rows.stream()
                        .filter(r -> r.reportId().equals(semesterId))
                        .findFirst()
                        .orElseThrow();
        assertThat(semester.type()).isEqualTo(ReportType.SEMESTER);
        assertThat(semester.teacherName()).isEqualTo("Bruno Lima");
        assertThat(semester.teacherActive()).isFalse();
        assertThat(semester.period().key()).isEqualTo("2026-1");
        assertThat(semester.lessonDate()).isNull();
        assertThat(semester.approvedVersion()).isEqualTo(2);
    }

    @Test
    void unknownStudentOrTeacherGivesAnEmptyPage() {
        teacher("Clara Souza", true);

        PageResult<ApprovedReportRow> page =
                query.page(
                        new ApprovedReportCriteria(
                                UUID.randomUUID(), UUID.randomUUID(), null, null, null),
                        PageRequest.first());

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }
}
