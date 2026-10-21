package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.LessonReportEntity;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Visão derivada "relatório em aberto" (data-model.md): relatórios de aula não aprovados nem
 * cancelados, registros ainda em transcrição e, quando houver, consultas complementares
 * (semestral).
 */
@Component
public class OpenReportsQueryAdapter implements OpenReportsQueryPort {

    /** Fonte adicional de relatórios em aberto (ex.: semestrais), registrada por outra história. */
    public interface OpenReportSource {
        List<OpenReport> listByTeacher(UUID teacherId);
    }

    private final LessonReportJpa reports;
    private final JobQueuePostgresAdapter queue;
    private final StudentJpa students;
    private final JdbcTemplate jdbc;
    private final SemesterReportRepositoryAdapter semester;
    private final List<OpenReportSource> fonts = new ArrayList<>();

    OpenReportsQueryAdapter(
            LessonReportJpa reports,
            JobQueuePostgresAdapter queue,
            StudentJpa students,
            JdbcTemplate jdbc,
            SemesterReportRepositoryAdapter semester) {
        this.semester = semester;
        this.reports = reports;
        this.queue = queue;
        this.students = students;
        this.jdbc = jdbc;
    }

    public void registerFont(OpenReportSource font) {
        fonts.add(font);
    }

    @Override
    public List<OpenReport> listByTeacher(UUID teacherId) {
        Map<UUID, String> names = new HashMap<>();
        List<OpenReport> list = new ArrayList<>();
        for (LessonReportEntity r : reports.listTeacherOpenReports(teacherId)) {
            list.add(ofReport(r, name(names, r.studentId)));
        }
        for (ProcessingJob t : queue.teacherRegistrationsInProgress(teacherId)) {
            list.add(ofJob(t, name(names, t.studentId())));
        }
        for (SemesterReport r : semester.teacherOpenReports(teacherId)) {
            list.add(
                    new OpenReport(
                            ReportType.SEMESTER,
                            r.id(),
                            null,
                            r.studentId(),
                            name(names, r.studentId()),
                            r.status(),
                            r.step() == null ? null : jobStep(r.step().name()),
                            r.period().key(),
                            r.originChannel(),
                            r.updatedAt()));
        }
        for (ProcessingJob t : queue.teacherConsolidationsInProgress(teacherId)) {
            list.add(
                    new OpenReport(
                            ReportType.SEMESTER,
                            null,
                            t.id(),
                            t.studentId(),
                            name(names, t.studentId()),
                            null,
                            t.step(),
                            t.parameter(ProcessingJob.PERIOD_KEY).orElse(null),
                            t.requestChannel(),
                            t.createdAt()));
        }
        for (OpenReportSource font : fonts) {
            list.addAll(font.listByTeacher(teacherId));
        }
        list.sort(Comparator.comparing(OpenReport::updatedAt).reversed());
        return list;
    }

    @Override
    public Optional<OpenReport> searchByStudent(UUID teacherId, UUID studentId) {
        Optional<LessonReportEntity> report = reports.searchOpen(teacherId, studentId);
        if (report.isPresent()) {
            return Optional.of(ofReport(report.get(), name(new HashMap<>(), studentId)));
        }
        return queue.searchRegistrationInProgress(teacherId, studentId)
                .map(t -> ofJob(t, name(new HashMap<>(), studentId)));
    }

    @Override
    public Map<UUID, LocalDate> lastLessonByStudent(UUID teacherId) {
        Map<UUID, LocalDate> lasts = new HashMap<>();
        jdbc.query(
                "select a.student_id, max(a.lesson_date) as last from lesson a"
                        + " join lesson_report r on r.lesson_id = a.id"
                        + " where a.teacher_id = ? and r.status <> 'CANCELLED' group by a.student_id",
                line -> {
                    Date data = line.getDate("last");
                    lasts.put(line.getObject("student_id", UUID.class), data.toLocalDate());
                },
                teacherId);
        return lasts;
    }

    @Override
    public int countOpenReports(UUID teacherId, UUID studentId) {
        throw PendingImplementation.of("W-C2");
    }

    private String name(Map<UUID, String> cache, UUID studentId) {
        return cache.computeIfAbsent(
                studentId, id -> students.findById(id).map(StudentEntity::getName).orElse(""));
    }

    private static OpenReport ofReport(LessonReportEntity r, String studentName) {
        return new OpenReport(
                ReportType.LESSON,
                r.id,
                null,
                r.studentId,
                studentName,
                ReportStatus.valueOf(r.status),
                r.processingStep == null ? null : jobStep(r.processingStep),
                null,
                Channel.valueOf(r.originChannel),
                r.updatedAt);
    }

    private static JobStep jobStep(String reportStep) {
        try {
            return JobStep.valueOf(reportStep);
        } catch (IllegalArgumentException withoutEquivalent) {
            return null;
        }
    }

    private static OpenReport ofJob(ProcessingJob t, String studentName) {
        return new OpenReport(
                ReportType.LESSON,
                null,
                t.id(),
                t.studentId(),
                studentName,
                ReportStatus.PROCESSING,
                t.step(),
                null,
                t.requestChannel(),
                t.startedAt() != null ? t.startedAt() : t.createdAt());
    }
}
