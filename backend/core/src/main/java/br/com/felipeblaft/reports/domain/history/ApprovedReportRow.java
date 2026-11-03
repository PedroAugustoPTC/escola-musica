package br.com.felipeblaft.reports.domain.history;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Item do histórico de relatórios aprovados (data-model.md §2.2). {@code lessonDate} só existe na
 * aula e {@code period} só no semestral. Os nomes vão apenas para a tela: o {@code toString} não os
 * revela, para não chegarem a log nem a autoria.
 */
public record ApprovedReportRow(
        ReportType type,
        UUID reportId,
        UUID studentId,
        String studentName,
        boolean studentActive,
        UUID teacherId,
        String teacherName,
        boolean teacherActive,
        LocalDate lessonDate,
        SemesterPeriod period,
        Instant approvedAt,
        int approvedVersion) {

    public ApprovedReportRow {
        Objects.requireNonNull(type);
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(approvedAt);
        if (type == ReportType.LESSON && lessonDate == null) {
            throw new IllegalArgumentException("relatório de aula exige a data da aula");
        }
        if (type == ReportType.SEMESTER && period == null) {
            throw new IllegalArgumentException("relatório semestral exige o período");
        }
        if (approvedVersion < 1) {
            throw new IllegalArgumentException("versão aprovada deve ser maior que zero");
        }
    }

    /** Data de referência da ordenação e do filtro de período. */
    public LocalDate reference() {
        return type == ReportType.LESSON ? lessonDate : period.start();
    }

    @Override
    public String toString() {
        return "ApprovedReportRow[type="
                + type
                + ", reportId="
                + reportId
                + ", approvedVersion="
                + approvedVersion
                + "]";
    }
}
