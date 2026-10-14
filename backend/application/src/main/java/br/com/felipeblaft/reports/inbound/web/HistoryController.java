package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.History;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.HistoryItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Histórico de relatórios aprovados do aluno (FR-047), com os mesmos rótulos do bot. */
@RestController
public class HistoryController {

    public record HistoryItemDto(
            String type,
            UUID reportId,
            String label,
            LocalDate lessonDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant approvedAt) {}

    public record HistoryDto(StudentSummaryDto student, List<HistoryItemDto> items) {}

    private final QueryStudentHistoryUseCase query;
    private final MessageFormatting formatting;

    public HistoryController(QueryStudentHistoryUseCase query, MessageFormatting formatting) {
        this.query = query;
        this.formatting = formatting;
    }

    @GetMapping("/api/v1/students/{studentId}/history")
    public HistoryDto history(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID studentId) {
        History history = query.run(teacher.id(), studentId);
        return new HistoryDto(
                HomeMapper.student(history.student()),
                history.items().stream().map(this::item).toList());
    }

    private HistoryItemDto item(HistoryItem item) {
        return item.type() == ReportType.LESSON
                ? new HistoryItemDto(
                        item.type().name(),
                        item.reportId(),
                        formatting.historyLabel(item),
                        item.lessonDate(),
                        null,
                        null,
                        item.approvedAt())
                : new HistoryItemDto(
                        item.type().name(),
                        item.reportId(),
                        formatting.historyLabel(item),
                        null,
                        item.period().start(),
                        item.period().end(),
                        item.approvedAt());
    }
}
