package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs e conversões da visão da escola (feature 006, {@code api-school-history.openapi.yaml}).
 * Nunca levam CPF, nascimento, e-mail nem responsável; o {@code toString} de todos omite nomes e
 * rótulos.
 */
public final class AdminSchoolHistoryDtos {

    private AdminSchoolHistoryDtos() {}

    /** Aluno ou professor: só identificador, nome e situação. */
    public record PersonSummary(UUID id, String name, boolean active) {

        @Override
        public String toString() {
            return "PersonSummary[id=" + id + ", active=" + active + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SchoolHistoryItem(
            String type,
            UUID reportId,
            String label,
            LocalDate lessonDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            PersonSummary student,
            PersonSummary teacher,
            Instant approvedAt,
            int approvedVersion) {

        @Override
        public String toString() {
            return "SchoolHistoryItem[type=" + type + ", reportId=" + reportId + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SchoolHistoryPage(
            PersonSummary student, List<SchoolHistoryItem> items, int page, int size, long total) {

        @Override
        public String toString() {
            return "SchoolHistoryPage[page=" + page + ", size=" + size + ", total=" + total + "]";
        }
    }

    /** Converte o resultado do caso de uso; o rótulo é o mesmo do histórico do professor. */
    static SchoolHistoryPage page(
            QuerySchoolHistoryUseCase.Result result, MessageFormatting formatting) {
        var student = result.student();
        var page = result.page();
        return new SchoolHistoryPage(
                student == null
                        ? null
                        : new PersonSummary(student.id(), student.name(), student.active()),
                page.items().stream().map(row -> item(row, formatting)).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    private static SchoolHistoryItem item(ApprovedReportRow row, MessageFormatting formatting) {
        boolean lesson = row.type() == ReportType.LESSON;
        String label = formatting.historyLabel(row.type(), row.lessonDate(), row.period());
        return new SchoolHistoryItem(
                row.type().name(),
                row.reportId(),
                label,
                lesson ? row.lessonDate() : null,
                lesson ? null : row.period().start(),
                lesson ? null : row.period().end(),
                new PersonSummary(row.studentId(), row.studentName(), row.studentActive()),
                new PersonSummary(row.teacherId(), row.teacherName(), row.teacherActive()),
                row.approvedAt(),
                row.approvedVersion());
    }
}
