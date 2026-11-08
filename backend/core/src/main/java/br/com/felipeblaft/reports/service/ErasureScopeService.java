package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.privacy.ErasureScope;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import java.util.Objects;
import java.util.UUID;

/** Calcula o alcance de uma eliminação só em números (FR-021): nunca lê conteúdo nem nome. */
public class ErasureScopeService {

    private final LessonReportRepository lessons;
    private final SemesterReportRepository semesters;
    private final PdfDocumentRepository pdfs;
    private final AssignmentRepository assignments;
    private final ConsentRepository consents;

    public ErasureScopeService(
            LessonReportRepository lessons,
            SemesterReportRepository semesters,
            PdfDocumentRepository pdfs,
            AssignmentRepository assignments,
            ConsentRepository consents) {
        this.lessons = Objects.requireNonNull(lessons);
        this.semesters = Objects.requireNonNull(semesters);
        this.pdfs = Objects.requireNonNull(pdfs);
        this.assignments = Objects.requireNonNull(assignments);
        this.consents = Objects.requireNonNull(consents);
    }

    public ErasureScope compute(UUID studentId) {
        int pdfCount = 0;
        for (UUID id : lessons.findIdsByStudent(studentId)) {
            pdfCount += pdfs.countByReport(ReportType.LESSON, id);
        }
        for (UUID id : semesters.findIdsByStudent(studentId)) {
            pdfCount += pdfs.countByReport(ReportType.SEMESTER, id);
        }
        return new ErasureScope(
                lessons.countByStudentAndStatus(studentId),
                semesters.countByStudentAndStatus(studentId),
                pdfCount,
                assignments.listTeachersOfStudent(studentId).size(),
                consents.findByStudent(studentId).size());
    }
}
