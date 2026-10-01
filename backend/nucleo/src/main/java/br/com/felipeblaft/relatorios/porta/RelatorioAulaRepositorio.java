package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
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
}
