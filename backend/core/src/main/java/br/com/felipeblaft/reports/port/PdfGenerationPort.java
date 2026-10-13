package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Geração do PDF com a identidade visual (contracts/portas-provedores.md §4, FR-049). O mesmo PDF
 * vale para os dois canais.
 */
public interface PdfGenerationPort {

    GeneratedDocument generateLessonReport(LessonReportPdfModel model);

    GeneratedDocument generateSemesterReport(SemesterReportPdfModel model);

    enum DocumentStatus {
        CONFERENCIA,
        OFICIAL
    }

    /** Quem aprovou, quando e qual versão (FR-030). */
    record Approval(String teacherName, Instant approvedAt, int version) {}

    record LessonReportPdfModel(
            String studentName,
            String teacherName,
            LocalDate lessonDate,
            LessonReportContent content,
            int version,
            DocumentStatus status,
            Approval approval) {

        public LessonReportPdfModel {
            Objects.requireNonNull(studentName);
            Objects.requireNonNull(teacherName);
            Objects.requireNonNull(lessonDate);
            Objects.requireNonNull(content);
            Objects.requireNonNull(status);
            if (status == DocumentStatus.OFICIAL) {
                Objects.requireNonNull(approval);
            }
        }
    }

    /**
     * Semestral: Informações Gerais (sistema), seções 2 a 10 e a quantidade de aulas consolidadas.
     */
    record SemesterReportPdfModel(
            GeneralInformation generalInformation,
            SemesterReportContent content,
            int version,
            DocumentStatus status,
            Approval approval) {

        public SemesterReportPdfModel {
            Objects.requireNonNull(generalInformation);
            Objects.requireNonNull(content);
            Objects.requireNonNull(status);
            if (status == DocumentStatus.OFICIAL) {
                Objects.requireNonNull(approval);
            }
        }
    }

    record GeneratedDocument(byte[] bytes, String sha256, long size) {}
}
