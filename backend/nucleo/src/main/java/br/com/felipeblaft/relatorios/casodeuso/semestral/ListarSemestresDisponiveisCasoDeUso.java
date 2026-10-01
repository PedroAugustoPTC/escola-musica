package br.com.felipeblaft.relatorios.casodeuso.semestral;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Semestres disponíveis para o semestral (FR-041): do semestre da primeira aula do aluno com o
 * professor (ou do cadastro, o que vier antes) até o corrente, do mais recente ao mais antigo, com
 * o mais recente pré-selecionado e o corrente marcado como "em andamento".
 */
public class ListarSemestresDisponiveisCasoDeUso {

    public record Semestre(PeriodoSemestral periodo, boolean emAndamento, boolean preSelecionado) {}

    private final AutorizacaoServico autorizacao;
    private final AlunoRepositorio alunos;
    private final RelatorioAulaRepositorio aulas;
    private final RelogioPorta relogio;

    public ListarSemestresDisponiveisCasoDeUso(
            AutorizacaoServico autorizacao,
            AlunoRepositorio alunos,
            RelatorioAulaRepositorio aulas,
            RelogioPorta relogio) {
        this.autorizacao = Objects.requireNonNull(autorizacao);
        this.alunos = Objects.requireNonNull(alunos);
        this.aulas = Objects.requireNonNull(aulas);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public List<Semestre> executar(UUID professorId, UUID alunoId) {
        autorizacao.exigirAssociacao(professorId, alunoId);
        Aluno aluno = alunos.buscarPorId(alunoId).orElseThrow();
        LocalDate hoje = relogio.hojeNoFusoDaEscola();
        LocalDate cadastro = aluno.criadoEm().atZone(relogio.fusoDaEscola()).toLocalDate();
        LocalDate desde =
                aulas.dataPrimeiraAula(professorId, alunoId)
                        .filter(primeira -> primeira.isBefore(cadastro))
                        .orElse(cadastro);
        List<Semestre> semestres = new ArrayList<>();
        List<PeriodoSemestral> periodos = PeriodoSemestral.disponiveis(desde, hoje);
        for (int i = 0; i < periodos.size(); i++) {
            PeriodoSemestral periodo = periodos.get(i);
            semestres.add(new Semestre(periodo, periodo.emAndamento(hoje), i == 0));
        }
        return semestres;
    }
}
