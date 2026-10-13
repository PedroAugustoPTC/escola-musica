package br.com.felipeblaft.reports.usecase.lesson;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ReportAccessService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado e conteúdo vigente do relatório de aula, iguais nos dois canais (FR-002). Relatório de
 * outro professor é {@code RECURSO_NAO_ENCONTRADO}.
 */
public class GetLessonReportUseCase {

    public record LessonReportView(
            LessonReport report,
            Lesson lesson,
            String studentName,
            String teacherName,
            Optional<ReportInteraction> lastInteraction,
            Optional<ProcessingJob> jobInProgress,
            boolean pdfAvailable) {}

    private final ReportAccessService access;
    private final LessonReportRepository reports;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final ReportInteractionRepository interactions;
    private final ReportVersionRepository versions;
    private final PdfDocumentRepository documents;
    private final JobQueuePort queue;

    public GetLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            StudentRepository students,
            TeacherRepository teachers,
            ReportInteractionRepository interactions,
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            JobQueuePort queue) {
        this.access = Objects.requireNonNull(access);
        this.reports = Objects.requireNonNull(reports);
        this.students = Objects.requireNonNull(students);
        this.teachers = Objects.requireNonNull(teachers);
        this.interactions = Objects.requireNonNull(interactions);
        this.versions = Objects.requireNonNull(versions);
        this.documents = Objects.requireNonNull(documents);
        this.queue = Objects.requireNonNull(queue);
    }

    public LessonReportView run(UUID teacherId, UUID reportId) {
        LessonReport report = access.lessonReport(teacherId, reportId);
        Lesson lesson = reports.searchLesson(report.lessonId()).orElseThrow();
        String student = students.searchById(report.studentId()).orElseThrow().name();
        String teacher = teachers.searchById(report.teacherId()).orElseThrow().name();
        boolean pdf =
                report.currentVersion() > 0
                        && versions.search(ReportType.AULA, report.id(), report.currentVersion())
                                .flatMap(v -> documents.search(v.id(), DocumentType.CONFERENCIA))
                                .isPresent();
        return new LessonReportView(
                report,
                lesson,
                student,
                teacher,
                interactions.last(ReportType.AULA, report.id()),
                queue.searchInProgressByReport(ReportType.AULA, report.id()),
                pdf);
    }
}
