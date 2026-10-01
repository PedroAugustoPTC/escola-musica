package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelatorioAulaRepositorio {

    void salvarAula(Aula aula);

    Optional<Aula> buscarAula(UUID aulaId);

    /**
     * @throws ConflitoConcorrenciaExcecao se outro canal gravou o relatório antes (bloqueio
     *     otimista) ou se já existe relatório em aberto para o par (índice único parcial)
     */
    void salvar(RelatorioAula relatorio);

    Optional<RelatorioAula> buscarPorId(UUID id);

    /**
     * Lê o relatório bloqueando a linha até o fim da transação: ações concorrentes dos dois canais
     * (ex.: aprovação dupla) são serializadas e a segunda vê o estado já gravado.
     */
    Optional<RelatorioAula> buscarParaAtualizar(UUID id);

    /** Relatório de aula em aberto do professor para o aluno (FR-034). */
    Optional<RelatorioAula> buscarEmAbertoDoAluno(UUID professorId, UUID alunoId);

    /** Relatórios de aula aprovados do professor para o aluno com aula no período (com a data). */
    List<AulaAprovada> listarAprovadosNoPeriodo(
            UUID professorId, UUID alunoId, LocalDate inicio, LocalDate fim);

    /** Quantos relatórios de aula do par seguem em aberto com aula no período. */
    int contarEmAbertoNoPeriodo(UUID professorId, UUID alunoId, LocalDate inicio, LocalDate fim);

    /** Todos os relatórios de aula aprovados do par (histórico), em qualquer ordem. */
    List<AulaAprovada> listarAprovados(UUID professorId, UUID alunoId);

    /** Data da primeira aula do aluno com o professor, se houver. */
    Optional<LocalDate> dataPrimeiraAula(UUID professorId, UUID alunoId);

    /** Relatório de aula aprovado com a data da aula e o conteúdo aprovado. */
    record AulaAprovada(
            UUID relatorioId,
            LocalDate dataAula,
            ConteudoRelatorioAula conteudo,
            Instant aprovadoEm) {}
}
