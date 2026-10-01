package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import java.util.Objects;
import java.util.UUID;

/**
 * Carrega relatórios só para o dono (FR-009, FR-048): relatório de outro professor, ou de aluno que
 * deixou de estar associado, é tratado como inexistente.
 */
public class AcessoRelatorioServico {

    private final RelatorioAulaRepositorio relatorios;
    private final AssociacaoRepositorio associacoes;

    public AcessoRelatorioServico(
            RelatorioAulaRepositorio relatorios, AssociacaoRepositorio associacoes) {
        this.relatorios = Objects.requireNonNull(relatorios);
        this.associacoes = Objects.requireNonNull(associacoes);
    }

    public RelatorioAula relatorioAula(UUID professorId, UUID relatorioId) {
        return doDono(relatorios.buscarPorId(relatorioId), professorId);
    }

    /** Como {@link #relatorioAula}, com a linha bloqueada para a atualização. */
    public RelatorioAula relatorioAulaParaAtualizar(UUID professorId, UUID relatorioId) {
        return doDono(relatorios.buscarParaAtualizar(relatorioId), professorId);
    }

    private RelatorioAula doDono(java.util.Optional<RelatorioAula> relatorio, UUID professorId) {
        return relatorio
                .filter(r -> r.professorId().equals(professorId))
                .filter(r -> associacoes.existe(professorId, r.alunoId()))
                .orElseThrow(() -> new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO));
    }
}
