package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import java.util.Objects;
import java.util.UUID;

/**
 * Regras de autorização comuns aos dois canais (FR-009, FR-010, Princípio IV): aluno exige
 * associação; relatório de outro professor é tratado como inexistente.
 */
public class AutorizacaoServico {

    private final AssociacaoRepositorio associacoes;

    public AutorizacaoServico(AssociacaoRepositorio associacoes) {
        this.associacoes = Objects.requireNonNull(associacoes);
    }

    public void exigirAssociacao(UUID professorId, UUID alunoId) {
        if (!associacoes.existe(professorId, alunoId)) {
            throw new ExcecaoDominio(CodigoErro.ALUNO_NAO_ASSOCIADO);
        }
    }

    public void exigirDono(UUID professorId, UUID professorDoRecurso) {
        if (!Objects.equals(professorId, professorDoRecurso)) {
            throw new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO);
        }
    }
}
