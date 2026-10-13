package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.entrada.web.seguranca.ProfessorAutenticado;
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
public class HistoricoControlador {

    public record ItemHistoricoDto(
            String tipo,
            UUID relatorioId,
            String rotulo,
            LocalDate dataAula,
            LocalDate periodoInicio,
            LocalDate periodoFim,
            Instant aprovadoEm) {}

    public record HistoricoDto(AlunoResumoDto aluno, List<ItemHistoricoDto> itens) {}

    private final QueryStudentHistoryUseCase consultar;
    private final FormatacaoMensagens formatacao;

    public HistoricoControlador(
            QueryStudentHistoryUseCase consultar, FormatacaoMensagens formatacao) {
        this.consultar = consultar;
        this.formatacao = formatacao;
    }

    @GetMapping("/api/v1/alunos/{alunoId}/historico")
    public HistoricoDto historico(
            @AuthenticationPrincipal ProfessorAutenticado professor, @PathVariable UUID alunoId) {
        History historico = consultar.run(professor.id(), alunoId);
        return new HistoricoDto(
                InicioMapeador.aluno(historico.student()),
                historico.items().stream().map(this::item).toList());
    }

    private ItemHistoricoDto item(HistoryItem item) {
        return item.type() == ReportType.AULA
                ? new ItemHistoricoDto(
                        item.type().name(),
                        item.reportId(),
                        formatacao.rotuloHistorico(item),
                        item.lessonDate(),
                        null,
                        null,
                        item.approvedAt())
                : new ItemHistoricoDto(
                        item.type().name(),
                        item.reportId(),
                        formatacao.rotuloHistorico(item),
                        null,
                        item.period().start(),
                        item.period().end(),
                        item.approvedAt());
    }
}
