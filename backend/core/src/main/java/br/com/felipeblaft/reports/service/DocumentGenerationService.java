package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.PdfGenerationPort;
import br.com.felipeblaft.reports.port.PdfGenerationPort.Approval;
import br.com.felipeblaft.reports.port.PdfGenerationPort.DocumentStatus;
import br.com.felipeblaft.reports.port.PdfGenerationPort.GeneratedDocument;
import br.com.felipeblaft.reports.port.PdfGenerationPort.LessonReportPdfModel;
import br.com.felipeblaft.reports.port.PdfGenerationPort.SemesterReportPdfModel;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Gera, guarda e registra os PDFs (R-10, R-11): o de conferência de cada versão e o oficial da
 * versão aprovada. A chave de armazenamento é interna: {@code relatorios/<tipo>/<id>/v<n>.pdf}.
 */
public class DocumentGenerationService {

    private static final String STORAGE_COMPONENT = "pdf-storage";

    private final PdfGenerationPort pdfGeneration;
    private final DocumentStoragePort storage;
    private final PdfDocumentRepository documents;
    private final LessonReportRepository reports;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final ClockPort clock;
    private final OperationalAlertService alerts;

    public DocumentGenerationService(
            PdfGenerationPort pdfGeneration,
            DocumentStoragePort storage,
            PdfDocumentRepository documents,
            LessonReportRepository reports,
            StudentRepository students,
            TeacherRepository teachers,
            ClockPort clock) {
        this(
                pdfGeneration,
                storage,
                documents,
                reports,
                students,
                teachers,
                clock,
                (type, component, detailCode) -> {});
    }

    @SuppressWarnings("java:S107")
    public DocumentGenerationService(
            PdfGenerationPort pdfGeneration,
            DocumentStoragePort storage,
            PdfDocumentRepository documents,
            LessonReportRepository reports,
            StudentRepository students,
            TeacherRepository teachers,
            ClockPort clock,
            OperationalAlertService alerts) {
        this.alerts = Objects.requireNonNull(alerts);
        this.pdfGeneration = Objects.requireNonNull(pdfGeneration);
        this.storage = Objects.requireNonNull(storage);
        this.documents = Objects.requireNonNull(documents);
        this.reports = Objects.requireNonNull(reports);
        this.students = Objects.requireNonNull(students);
        this.teachers = Objects.requireNonNull(teachers);
        this.clock = Objects.requireNonNull(clock);
    }

    public static String key(ReportType type, UUID reportId, String file) {
        return "reports/" + type.name().toLowerCase(Locale.ROOT) + "/" + reportId + "/" + file;
    }

    public PdfDocument generateReview(LessonReport report, ReportVersion version) {
        return generateLesson(report, version, DocumentStatus.REVIEW, null);
    }

    public PdfDocument generateOfficial(LessonReport report, ReportVersion version) {
        Teacher teacher = teacher(report.teacherId());
        Approval approval =
                new Approval(teacher.name(), report.approvedAt(), report.approvedVersion());
        return generateLesson(report, version, DocumentStatus.OFFICIAL, approval);
    }

    private PdfDocument generateLesson(
            LessonReport report, ReportVersion version, DocumentStatus status, Approval approval) {
        Lesson lesson = reports.searchLesson(report.lessonId()).orElseThrow();
        Student student = students.searchById(report.studentId()).orElseThrow();
        Teacher teacher = teacher(report.teacherId());
        LessonReportPdfModel model =
                new LessonReportPdfModel(
                        student.name(),
                        teacher.name(),
                        lesson.lessonDate(),
                        (LessonReportContent) version.content(),
                        version.number(),
                        status,
                        approval);
        GeneratedDocument generated = pdfGeneration.generateLessonReport(model);
        String file =
                status == DocumentStatus.OFFICIAL
                        ? "official.pdf"
                        : "v" + version.number() + ".pdf";
        return store(ReportType.LESSON, report.id(), version, status, generated, file);
    }

    public PdfDocument generateReview(SemesterReport report, ReportVersion version) {
        return generateSemester(report, version, DocumentStatus.REVIEW, null);
    }

    public PdfDocument generateOfficial(SemesterReport report, ReportVersion version) {
        Teacher teacher = teacher(report.teacherId());
        Approval approval =
                new Approval(teacher.name(), report.approvedAt(), report.approvedVersion());
        return generateSemester(report, version, DocumentStatus.OFFICIAL, approval);
    }

    private PdfDocument generateSemester(
            SemesterReport report,
            ReportVersion version,
            DocumentStatus status,
            Approval approval) {
        SemesterReportPdfModel model =
                new SemesterReportPdfModel(
                        report.generalInformation(),
                        (SemesterReportContent) version.content(),
                        version.number(),
                        status,
                        approval);
        GeneratedDocument generated = pdfGeneration.generateSemesterReport(model);
        String file =
                status == DocumentStatus.OFFICIAL
                        ? "official.pdf"
                        : "v" + version.number() + ".pdf";
        return store(ReportType.SEMESTER, report.id(), version, status, generated, file);
    }

    /** Guarda os bytes e registra o documento (usado também pelo semestral). */
    public PdfDocument store(
            ReportType type,
            UUID reportId,
            ReportVersion version,
            DocumentStatus status,
            GeneratedDocument generated,
            String file) {
        String key = key(type, reportId, file);
        try {
            storage.save(key, generated.bytes());
        } catch (RuntimeException failure) {
            alerts.report(AlertType.STORAGE_FAILED, STORAGE_COMPONENT, "WRITE_FAILED");
            throw failure;
        }
        PdfDocument document =
                PdfDocument.create(
                        version.id(),
                        status == DocumentStatus.OFFICIAL
                                ? DocumentType.OFFICIAL
                                : DocumentType.REVIEW,
                        key,
                        generated.size(),
                        generated.sha256(),
                        clock.now());
        documents.save(document);
        return document;
    }

    public Optional<byte[]> open(PdfDocument document) {
        try {
            return storage.open(document.storageKey());
        } catch (RuntimeException failure) {
            alerts.report(AlertType.STORAGE_FAILED, STORAGE_COMPONENT, "READ_FAILED");
            throw failure;
        }
    }

    private Teacher teacher(UUID id) {
        return teachers.searchById(id).orElseThrow();
    }
}
