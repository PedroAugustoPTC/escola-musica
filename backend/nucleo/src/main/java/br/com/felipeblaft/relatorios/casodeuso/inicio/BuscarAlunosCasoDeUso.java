package br.com.felipeblaft.relatorios.casodeuso.inicio;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.BuscaAluno;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Busca aproximada entre os alunos associados (FR-013, R-17), igual nos dois canais: vazia → todos;
 * com correspondências → a lista; sem nenhuma → {@code NENHUM} com todos os alunos do professor.
 */
public class BuscarAlunosCasoDeUso {

    public record Resultado(BuscaAluno.TipoResultado tipo, List<AlunoResumo> alunos) {}

    private final AlunoRepositorio alunos;
    private final RelatoriosEmAbertoConsultaPorta consulta;

    public BuscarAlunosCasoDeUso(AlunoRepositorio alunos, RelatoriosEmAbertoConsultaPorta consulta) {
        this.alunos = Objects.requireNonNull(alunos);
        this.consulta = Objects.requireNonNull(consulta);
    }

    public Resultado executar(UUID professorId, String busca) {
        List<Aluno> todos = alunos.listarDoProfessor(professorId);
        BuscaAluno.Resultado resultado = BuscaAluno.buscar(todos, busca);
        ResumosAlunos resumos =
                ResumosAlunos.carregar(consulta, professorId, consulta.listarDoProfessor(professorId));
        return new Resultado(resultado.tipo(), resumos.resumos(resultado.alunos()));
    }

    /** Correspondência exata a um único aluno (usada pelo bot durante a conferência). */
    public Optional<AlunoResumo> correspondenciaExata(UUID professorId, String texto) {
        List<Aluno> todos = alunos.listarDoProfessor(professorId);
        return BuscaAluno.correspondenciaExata(todos, texto)
                .map(aluno -> new AlunoResumo(aluno.id(), aluno.nome(), aluno.instrumento(), null, false));
    }
}
