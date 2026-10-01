package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Semestral só do próprio professor e de aluno associado; outro caso é RECURSO_NAO_ENCONTRADO. */
public class AcessoSemestralServico {

    private final RelatorioSemestralRepositorio semestrais;
    private final AssociacaoRepositorio associacoes;

    public AcessoSemestralServico(
            RelatorioSemestralRepositorio semestrais, AssociacaoRepositorio associacoes) {
        this.semestrais = Objects.requireNonNull(semestrais);
        this.associacoes = Objects.requireNonNull(associacoes);
    }

    public RelatorioSemestral relatorio(UUID professorId, UUID relatorioId) {
        return doDono(semestrais.buscarPorId(relatorioId), professorId);
    }

    public RelatorioSemestral relatorioParaAtualizar(UUID professorId, UUID relatorioId) {
        return doDono(semestrais.buscarParaAtualizar(relatorioId), professorId);
    }

    private RelatorioSemestral doDono(Optional<RelatorioSemestral> relatorio, UUID professorId) {
        return relatorio
                .filter(r -> r.professorId().equals(professorId))
                .filter(r -> associacoes.existe(professorId, r.alunoId()))
                .orElseThrow(() -> new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO));
    }
}
