package br.com.felipeblaft.relatorios.casodeuso.inicio;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import java.util.List;
import java.util.Objects;

/**
 * Início dos dois canais (FR-012): saudação, relatórios em aberto, total de alunos associados e os
 * primeiros alunos. Sem alunos, o canal mostra {@code SEM_ALUNOS}.
 */
public class ObterInicioProfessorCasoDeUso {

    public static final int PRIMEIROS_ALUNOS = 5;

    public record Inicio(
            Professor professor,
            int totalAlunos,
            List<AlunoResumo> alunos,
            List<RelatorioEmAberto> relatoriosEmAberto) {

        public boolean semAlunos() {
            return totalAlunos == 0;
        }
    }

    private final AlunoRepositorio alunos;
    private final RelatoriosEmAbertoConsultaPorta consulta;

    public ObterInicioProfessorCasoDeUso(
            AlunoRepositorio alunos, RelatoriosEmAbertoConsultaPorta consulta) {
        this.alunos = Objects.requireNonNull(alunos);
        this.consulta = Objects.requireNonNull(consulta);
    }

    public Inicio executar(Professor professor) {
        List<Aluno> todos = alunos.listarDoProfessor(professor.id());
        List<RelatorioEmAberto> emAberto = consulta.listarDoProfessor(professor.id());
        ResumosAlunos resumos = ResumosAlunos.carregar(consulta, professor.id(), emAberto);
        List<AlunoResumo> primeiros =
                resumos.resumos(todos.subList(0, Math.min(PRIMEIROS_ALUNOS, todos.size())));
        return new Inicio(professor, todos.size(), primeiros, emAberto);
    }
}
