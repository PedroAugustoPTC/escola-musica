package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fila de trabalhos persistida (R-06). */
public interface FilaTrabalhoPorta {

    /**
     * @throws ConflitoConcorrenciaExcecao se já houver registro de aula em curso para o par
     *     professor–aluno (índice único parcial)
     */
    void enfileirar(TrabalhoProcessamento trabalho);

    void atualizar(TrabalhoProcessamento trabalho);

    Optional<TrabalhoProcessamento> buscarPorId(UUID id);

    /**
     * Reserva o próximo trabalho pendente (sem disputar com outros workers) e o marca em execução.
     */
    Optional<TrabalhoProcessamento> reservarProximo(Instant agora);

    Optional<TrabalhoProcessamento> buscarEmCursoDoRelatorio(TipoRelatorio tipo, UUID relatorioId);

    /** Registro de aula (ou consolidação) ainda em curso para o aluno, sem relatório criado. */
    Optional<TrabalhoProcessamento> buscarRegistroEmCurso(UUID professorId, UUID alunoId);

    /**
     * Marca o aviso de demora só se ainda não foi marcado; devolve {@code true} se marcou agora.
     */
    boolean marcarAvisoDemora(UUID trabalhoId);

    /** Trabalhos em curso criados antes do limite e ainda sem aviso de demora. */
    List<TrabalhoProcessamento> listarDemoradosSemAviso(Instant criadosAntesDe);
}
