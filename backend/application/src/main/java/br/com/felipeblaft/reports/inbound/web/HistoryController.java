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
            String tipo,
            UUID relatorioId,
            String rotulo,
            LocalDate dataAula,
            LocalDate periodoInicio,
            LocalDate periodoFim,
            Instant aprovadoEm) {}

    public record HistoryDto(StudentSummaryDto aluno, List<HistoryItemDto> itens) {}

    private final QueryStudentHistoryUseCase query;
    private final MessageFormatting formatting;

    public HistoryController(QueryStudentHistoryUseCase query, MessageFormatting formatting) {
        this.query = query;
        this.formatting = formatting;
    }

    @GetMapping("/api/v1/alunos/{alunoId}/historico")
    public HistoryDto history(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID alunoId) {
        History history = query.run(teacher.id(), alunoId);
        return new HistoryDto(
                HomeMapper.student(history.student()),
                history.items().stream().map(this::item).toList());
    }

    private HistoryItemDto item(HistoryItem item) {
        return item.type() == ReportType.AULA
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
