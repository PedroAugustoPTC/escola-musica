package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelatorioSemestralRepositorio {

    /**
     * @throws ConflitoConcorrenciaExcecao se outro canal gravou antes (bloqueio otimista) ou se já
     *     existe semestral aprovado ou em aberto para o trio professor–aluno–período
     */
    void salvar(RelatorioSemestral relatorio);

    Optional<RelatorioSemestral> buscarPorId(UUID id);

    /** Lê bloqueando a linha até o fim da transação (ações concorrentes dos dois canais). */
    Optional<RelatorioSemestral> buscarParaAtualizar(UUID id);

    Optional<RelatorioSemestral> buscarAprovado(
            UUID professorId, UUID alunoId, String periodoChave);

    Optional<RelatorioSemestral> buscarEmAberto(
            UUID professorId, UUID alunoId, String periodoChave);

    /**
     * Semestrais aprovados do professor para o aluno (histórico), do mais recente ao mais antigo.
     */
    List<RelatorioSemestral> listarAprovados(UUID professorId, UUID alunoId);
}
