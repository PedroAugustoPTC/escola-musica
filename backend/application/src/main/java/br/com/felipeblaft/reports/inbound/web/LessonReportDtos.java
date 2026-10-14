package br.com.felipeblaft.reports.inbound.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs do relatório de aula e dos trabalhos (contracts/api-portal.openapi.yaml). */
public final class LessonReportDtos {

    private LessonReportDtos() {}

    public record JobRefDto(UUID trabalhoId) {}

    public record JobDto(
            UUID trabalhoId,
            String tipo,
            String status,
            String etapa,
            boolean demorando,
            String resultadoCodigo,
            String relatorioTipo,
            UUID relatorioId,
            String textoEntendido) {}

    public record LessonReportContentDto(
            List<String> conteudosTrabalhados,
            String evolucao,
            List<String> dificuldades,
            List<String> atividadesPropostas,
            String observacoes) {}

    public record InteractionDto(
            String tipo, String canal, String meio, String texto, Instant criadoEm) {}

    public record LessonReportDto(
            UUID relatorioId,
            UUID alunoId,
            String alunoNome,
            String professorNome,
            LocalDate dataAula,
            String status,
            String etapa,
            UUID trabalhoEmCursoId,
            int versaoVigente,
            String canalOrigem,
            LessonReportContentDto conteudo,
            List<String> perguntasPendentes,
            int rodadasPerguntas,
            InteractionDto ultimaInteracao,
            boolean pdfDisponivel,
            Instant aprovadoEm,
            Instant atualizadoEm) {}

    public record TextAnswerRequest(@Size(max = 4000) String texto) {}

    public record ChangeTextRequest(
            @Size(max = 4000) String texto, @NotNull @Min(1) Integer versaoBase) {}

    public record ApprovalRequest(@NotNull @Min(1) Integer versaoConfirmada) {}

    public record ApprovalDto(
            UUID relatorioId, String status, int versaoAprovada, Instant aprovadoEm) {}
}
