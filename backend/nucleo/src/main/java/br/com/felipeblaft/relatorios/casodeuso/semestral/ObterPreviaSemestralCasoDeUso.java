package br.com.felipeblaft.relatorios.casodeuso.semestral;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Prévia do semestral (FR-042): quantas aulas aprovadas e quantos relatórios de aula em aberto há
 * no período, e se já existe semestral aprovado ou em aberto (incluindo consolidação em curso).
 */
public class ObterPreviaSemestralCasoDeUso {

    public record Previa(
            PeriodoSemestral periodo,
            int aulasAprovadas,
            int relatoriosAulaEmAberto,
            Optional<UUID> semestralAprovadoId,
            Optional<RelatorioEmAberto> semestralEmAberto) {}

    private final AutorizacaoServico autorizacao;
    private final AlunoRepositorio alunos;
    private final RelatorioAulaRepositorio aulas;
    private final RelatorioSemestralRepositorio semestrais;
    private final FilaTrabalhoPorta fila;

    public ObterPreviaSemestralCasoDeUso(
            AutorizacaoServico autorizacao,
            AlunoRepositorio alunos,
            RelatorioAulaRepositorio aulas,
            RelatorioSemestralRepositorio semestrais,
            FilaTrabalhoPorta fila) {
        this.autorizacao = Objects.requireNonNull(autorizacao);
        this.alunos = Objects.requireNonNull(alunos);
        this.aulas = Objects.requireNonNull(aulas);
        this.semestrais = Objects.requireNonNull(semestrais);
        this.fila = Objects.requireNonNull(fila);
    }

    public Previa executar(UUID professorId, UUID alunoId, String periodoChave) {
        autorizacao.exigirAssociacao(professorId, alunoId);
        PeriodoSemestral periodo = PeriodoSemestral.de(periodoChave);
        int aprovadas =
                aulas.listarAprovadosNoPeriodo(
                                professorId, alunoId, periodo.inicio(), periodo.fim())
                        .size();
        int emAberto =
                aulas.contarEmAbertoNoPeriodo(
                        professorId, alunoId, periodo.inicio(), periodo.fim());
        Optional<UUID> aprovado =
                semestrais
                        .buscarAprovado(professorId, alunoId, periodo.chave())
                        .map(RelatorioSemestral::id);
        return new Previa(
                periodo,
                aprovadas,
                emAberto,
                aprovado,
                semestralEmAberto(professorId, alunoId, periodo));
    }

    /** Semestral em aberto do trio, ou consolidação ainda em curso (FR-034). */
    Optional<RelatorioEmAberto> semestralEmAberto(
            UUID professorId, UUID alunoId, PeriodoSemestral periodo) {
        String nome = alunos.buscarPorId(alunoId).map(a -> a.nome()).orElse("");
        Optional<RelatorioEmAberto> relatorio =
                semestrais
                        .buscarEmAberto(professorId, alunoId, periodo.chave())
                        .map(
                                r ->
                                        new RelatorioEmAberto(
                                                TipoRelatorio.SEMESTRAL,
                                                r.id(),
                                                null,
                                                alunoId,
                                                nome,
                                                r.status(),
                                                null,
                                                periodo.chave(),
                                                r.canalOrigem(),
                                                r.atualizadoEm()));
        if (relatorio.isPresent()) {
            return relatorio;
        }
        Optional<TrabalhoProcessamento> consolidacao =
                fila.buscarConsolidacaoEmCurso(professorId, alunoId, periodo.chave());
        return consolidacao.map(
                t ->
                        new RelatorioEmAberto(
                                TipoRelatorio.SEMESTRAL,
                                null,
                                t.id(),
                                alunoId,
                                nome,
                                null,
                                t.etapa(),
                                periodo.chave(),
                                t.canalSolicitacao(),
                                t.criadoEm()));
    }
}
