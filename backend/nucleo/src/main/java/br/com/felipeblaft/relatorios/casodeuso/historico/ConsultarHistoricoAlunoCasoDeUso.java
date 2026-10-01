package br.com.felipeblaft.relatorios.casodeuso.historico;

import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterResumoAlunoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Histórico do aluno (FR-047): relatórios de aula e semestrais aprovados do professor, do mais
 * recente ao mais antigo — aula pela data da aula, semestral pelo início do período, empate pela
 * aprovação. Igual nos dois canais.
 */
public class ConsultarHistoricoAlunoCasoDeUso {

    /** Item do histórico; {@code dataAula} só na aula e {@code periodo} só no semestral. */
    public record ItemHistorico(
            TipoRelatorio tipo,
            UUID relatorioId,
            LocalDate dataAula,
            PeriodoSemestral periodo,
            Instant aprovadoEm) {

        LocalDate referencia() {
            return tipo == TipoRelatorio.AULA ? dataAula : periodo.inicio();
        }
    }

    public record Historico(AlunoResumo aluno, List<ItemHistorico> itens) {}

    static final Comparator<ItemHistorico> MAIS_RECENTE_PRIMEIRO =
            Comparator.comparing(ItemHistorico::referencia)
                    .thenComparing(ItemHistorico::aprovadoEm)
                    .reversed();

    private final ObterResumoAlunoCasoDeUso resumoAluno;
    private final RelatorioAulaRepositorio aulas;
    private final RelatorioSemestralRepositorio semestrais;

    public ConsultarHistoricoAlunoCasoDeUso(
            ObterResumoAlunoCasoDeUso resumoAluno,
            RelatorioAulaRepositorio aulas,
            RelatorioSemestralRepositorio semestrais) {
        this.resumoAluno = Objects.requireNonNull(resumoAluno);
        this.aulas = Objects.requireNonNull(aulas);
        this.semestrais = Objects.requireNonNull(semestrais);
    }

    public Historico executar(UUID professorId, UUID alunoId) {
        AlunoResumo aluno = resumoAluno.executar(professorId, alunoId);
        List<ItemHistorico> itens = new ArrayList<>();
        aulas.listarAprovados(professorId, alunoId)
                .forEach(
                        a ->
                                itens.add(
                                        new ItemHistorico(
                                                TipoRelatorio.AULA,
                                                a.relatorioId(),
                                                a.dataAula(),
                                                null,
                                                a.aprovadoEm())));
        semestrais
                .listarAprovados(professorId, alunoId)
                .forEach(
                        s ->
                                itens.add(
                                        new ItemHistorico(
                                                TipoRelatorio.SEMESTRAL,
                                                s.id(),
                                                null,
                                                s.periodo(),
                                                s.aprovadoEm())));
        itens.sort(MAIS_RECENTE_PRIMEIRO);
        return new Historico(aluno, List.copyOf(itens));
    }
}
