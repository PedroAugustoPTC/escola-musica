package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportCriteria;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.history.HistoryOrder;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryLessonReportRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemorySemesterReportRepository;
import br.com.felipeblaft.reports.port.ApprovedReportQueryPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dublê da consulta de aprovados: deriva os itens dos repositórios em memória (só relatórios
 * aprovados), aplica os filtros pela data de referência (inclusive), ordena por {@link
 * HistoryOrder#NEWEST_FIRST} e pagina. Também aceita itens avulsos por {@link #add}.
 */
public class InMemoryApprovedReportQuery implements ApprovedReportQueryPort {

    private final InMemoryLessonReportRepository lessonReports;
    private final InMemorySemesterReportRepository semesterReports;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final List<ApprovedReportRow> extras = new ArrayList<>();

    public InMemoryApprovedReportQuery(
            InMemoryLessonReportRepository lessonReports,
            InMemorySemesterReportRepository semesterReports,
            StudentRepository students,
            TeacherRepository teachers) {
        this.lessonReports = lessonReports;
        this.semesterReports = semesterReports;
        this.students = students;
        this.teachers = teachers;
    }

    /** Item avulso, para testes que não montam relatórios pelos casos de uso. */
    public void add(ApprovedReportRow row) {
        extras.add(row);
    }

    @Override
    public PageResult<ApprovedReportRow> page(ApprovedReportCriteria criteria, PageRequest request) {
        return PageResult.slice(all(criteria), request);
    }

    @Override
    public List<ApprovedReportRow> all(ApprovedReportCriteria criteria) {
        List<ApprovedReportRow> rows = new ArrayList<>(extras);
        for (LessonReport report : lessonReports.reports.values()) {
            if (report.status() == ReportStatus.APPROVED) {
                rows.add(lessonRow(report));
            }
        }
        for (SemesterReport report : semesterReports.reports.values()) {
            if (report.status() == ReportStatus.APPROVED) {
                rows.add(semesterRow(report));
            }
        }
        return rows.stream()
                .filter(row -> matches(criteria, row))
                .sorted(HistoryOrder.NEWEST_FIRST)
                .toList();
    }

    private static boolean matches(ApprovedReportCriteria criteria, ApprovedReportRow row) {
        if (criteria.studentId() != null && !criteria.studentId().equals(row.studentId())) {
            return false;
        }
        if (criteria.teacherId() != null && !criteria.teacherId().equals(row.teacherId())) {
            return false;
        }
        if (criteria.type() != null && criteria.type() != row.type()) {
            return false;
        }
        LocalDate reference = row.reference();
        if (criteria.from() != null && reference.isBefore(criteria.from())) {
            return false;
        }
        return criteria.to() == null || !reference.isAfter(criteria.to());
    }

    private ApprovedReportRow lessonRow(LessonReport report) {
        LocalDate lessonDate = lessonReports.lessons.get(report.lessonId()).lessonDate();
        return row(
                ReportType.LESSON,
                report.id(),
                report.studentId(),
                report.teacherId(),
                lessonDate,
                null,
                report.approvedAt(),
                report.approvedVersion());
    }

    private ApprovedReportRow semesterRow(SemesterReport report) {
        return row(
                ReportType.SEMESTER,
                report.id(),
                report.studentId(),
                report.teacherId(),
                null,
                report.period(),
                report.approvedAt(),
                report.approvedVersion());
    }

    private ApprovedReportRow row(
            ReportType type,
            UUID reportId,
            UUID studentId,
            UUID teacherId,
            LocalDate lessonDate,
            br.com.felipeblaft.reports.domain.semester.SemesterPeriod period,
            java.time.Instant approvedAt,
            Integer approvedVersion) {
        Student student = students.searchById(studentId).orElseThrow();
        Teacher teacher = teachers.searchById(teacherId).orElseThrow();
        return new ApprovedReportRow(
                type,
                reportId,
                studentId,
                student.name(),
                student.active(),
                teacherId,
                teacher.name(),
                teacher.active(),
                lessonDate,
                period,
                approvedAt,
                approvedVersion);
    }
}
