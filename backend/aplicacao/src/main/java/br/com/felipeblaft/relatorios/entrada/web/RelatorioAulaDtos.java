package br.com.felipeblaft.relatorios.entrada.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs do relatório de aula e dos trabalhos (contracts/api-portal.openapi.yaml). */
public final class RelatorioAulaDtos {

    private RelatorioAulaDtos() {}

    public record TrabalhoRefDto(UUID trabalhoId) {}

    public record TrabalhoDto(
            UUID trabalhoId,
            String tipo,
            String status,
            String etapa,
            boolean demorando,
            String resultadoCodigo,
            String relatorioTipo,
            UUID relatorioId,
            String textoEntendido) {}

    public record ConteudoRelatorioAulaDto(
            List<String> conteudosTrabalhados,
            String evolucao,
            List<String> dificuldades,
            List<String> atividadesPropostas,
            String observacoes) {}

    public record InteracaoDto(
            String tipo, String canal, String meio, String texto, Instant criadoEm) {}

    public record RelatorioAulaDto(
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
            ConteudoRelatorioAulaDto conteudo,
            List<String> perguntasPendentes,
            int rodadasPerguntas,
            InteracaoDto ultimaInteracao,
            boolean pdfDisponivel,
            Instant aprovadoEm,
            Instant atualizadoEm) {}

    public record RespostaTextoRequisicao(@Size(max = 4000) String texto) {}

    public record AlteracaoTextoRequisicao(
            @Size(max = 4000) String texto, @NotNull @Min(1) Integer versaoBase) {}

    public record AprovacaoRequisicao(@NotNull @Min(1) Integer versaoConfirmada) {}

    public record AprovacaoDto(
            UUID relatorioId, String status, int versaoAprovada, Instant aprovadoEm) {}
}
