package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.history.ApprovedReportCriteria;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.port.ApprovedReportQueryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Desempenho da consulta de aprovados com Postgres real (feature 006, US3, SC-008): base com 10.000
 * relatórios aprovados (aula e semestral, vários professores e alunos); a primeira página sem
 * filtro, só por professor e só por período fica com p95 de até 300 ms em 20 execuções (meta
 * interna; o SC-008 pede até 2 s).
 */
class ApprovedReportQueryPerformanceIT extends RegistryPersistenceBase {

    private static final Instant BASE = Instant.parse("2026-08-01T12:00:00Z");
    private static final int TEACHERS = 10;
    private static final int STUDENTS = 40;
    private static final int LESSONS = 8_000;
    private static final int SEMESTERS = 2_000;
    private static final int RUNS = 20;
    private static final long LIMIT_MS = 300;

    @Autowired ApprovedReportQueryPort query;

    private final List<Teacher> teacherList = new ArrayList<>();
    private final List<Student> studentList = new ArrayList<>();

    @BeforeEach
    void seed() {
        for (int t = 0; t < TEACHERS; t++) {
            teacherList.add(teacher("Professor " + (char) ('A' + t), true));
        }
        for (int s = 0; s < STUDENTS; s++) {
            studentList.add(studentOf(teacherList.get(s % TEACHERS), "Aluno " + s));
        }
        List<Object[]> lessons = new ArrayList<>();
        List<Object[]> reports = new ArrayList<>();
        for (int i = 0; i < LESSONS; i++) {
            int s = i % STUDENTS;
            Teacher teacher = teacherList.get((s + i / 400) % TEACHERS);
            UUID lessonId = UUID.randomUUID();
            Timestamp approvedAt = Timestamp.from(BASE.plusSeconds(i));
            lessons.add(
                    new Object[] {
                        lessonId,
                        teacher.id(),
                        studentList.get(s).id(),
                        LocalDate.of(2020, 1, 1).plusDays(i / STUDENTS),
                        Timestamp.from(BASE)
                    });
            reports.add(
                    new Object[] {
                        UUID.randomUUID(),
                        lessonId,
                        teacher.id(),
                        studentList.get(s).id(),
                        Timestamp.from(BASE),
                        Timestamp.from(BASE),
                        approvedAt
                    });
        }
        jdbc.batchUpdate(
                "insert into lesson (id, teacher_id, student_id, lesson_date, created_at)"
                        + " values (?, ?, ?, ?, ?)",
                lessons);
        jdbc.batchUpdate(
                "insert into lesson_report (id, lesson_id, teacher_id, student_id, transcription,"
                        + " content, pending_questions, question_rounds, status, current_version,"
                        + " origin_channel, created_at, updated_at, approved_at, approved_version,"
                        + " approval_channel, processing_time_ms, lock_version) values (?, ?, ?, ?,"
                        + " 'texto', '{}'::jsonb, '[]'::jsonb, 0, 'APPROVED', 1, 'WEB', ?, ?, ?, 1,"
                        + " 'WEB', 0, 0)",
                reports);
        List<Object[]> semesters = new ArrayList<>();
        for (int i = 0; i < SEMESTERS; i++) {
            int s = i % STUDENTS;
            int periodIndex = i / STUDENTS;
            String key = (2000 + periodIndex / 2) + "-" + (periodIndex % 2 + 1);
            SemesterPeriod period = SemesterPeriod.of(key);
            Teacher teacher = teacherList.get(s % TEACHERS);
            semesters.add(
                    new Object[] {
                        UUID.randomUUID(),
                        teacher.id(),
                        studentList.get(s).id(),
                        key,
                        period.start(),
                        period.end(),
                        Timestamp.from(BASE),
                        Timestamp.from(BASE),
                        Timestamp.from(BASE.plusSeconds(i))
                    });
        }
        jdbc.batchUpdate(
                "insert into semester_report (id, teacher_id, student_id, period_key,"
                        + " period_start, period_end, considered_lesson_count, open_report_count,"
                        + " general_information, content, status, current_version, origin_channel,"
                        + " created_at, updated_at, approved_at, approved_version, approval_channel,"
                        + " processing_time_ms, lock_version) values (?, ?, ?, ?, ?, ?, 1, 0,"
                        + " '{}'::jsonb, '{}'::jsonb, 'APPROVED', 1, 'WEB', ?, ?, ?, 1, 'WEB', 0,"
                        + " 0)",
                semesters);
        jdbc.execute("analyze lesson_report");
        jdbc.execute("analyze semester_report");
        jdbc.execute("analyze lesson");
    }

    private long p95Millis(ApprovedReportCriteria criteria) {
        long[] times = new long[RUNS];
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            PageResult<ApprovedReportRow> page = query.page(criteria, PageRequest.first());
            times[run] = (System.nanoTime() - start) / 1_000_000;
            assertThat(page.items()).isNotEmpty();
            assertThat(page.total()).isPositive();
        }
        Arrays.sort(times);
        return times[(int) Math.ceil(RUNS * 0.95) - 1];
    }

    @Test
    void firstPageWithoutFilterStaysWithinTheTarget() {
        query.page(ApprovedReportCriteria.unfiltered(), PageRequest.first()); // aquecimento

        assertThat(p95Millis(ApprovedReportCriteria.unfiltered())).isLessThanOrEqualTo(LIMIT_MS);
    }

    @Test
    void teacherOnlyStaysWithinTheTarget() {
        ApprovedReportCriteria criteria =
                new ApprovedReportCriteria(null, teacherList.get(3).id(), null, null, null);
        query.page(criteria, PageRequest.first());

        assertThat(p95Millis(criteria)).isLessThanOrEqualTo(LIMIT_MS);
    }

    @Test
    void periodOnlyStaysWithinTheTarget() {
        ApprovedReportCriteria criteria =
                new ApprovedReportCriteria(
                        null, null, null, LocalDate.of(2020, 3, 1), LocalDate.of(2020, 6, 30));
        query.page(criteria, PageRequest.first());

        assertThat(p95Millis(criteria)).isLessThanOrEqualTo(LIMIT_MS);
    }
}
