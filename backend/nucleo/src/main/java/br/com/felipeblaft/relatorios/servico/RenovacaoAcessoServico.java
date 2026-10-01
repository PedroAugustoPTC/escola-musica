package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.Objects;

/**
 * Renova a validade do código de acesso a cada uso com sessão ou vínculo ativos, em qualquer canal
 * (FR-005). O domínio limita a gravação a uma vez por hora.
 */
public class RenovacaoAcessoServico {

    private final ProfessorRepositorio professores;
    private final RelogioPorta relogio;
    private final ParametrosNegocio parametros;

    public RenovacaoAcessoServico(
            ProfessorRepositorio professores, RelogioPorta relogio, ParametrosNegocio parametros) {
        this.professores = Objects.requireNonNull(professores);
        this.relogio = Objects.requireNonNull(relogio);
        this.parametros = Objects.requireNonNull(parametros);
    }

    public void renovar(Professor professor) {
        if (professor.renovarValidade(relogio.agora(), parametros.validadeCodigo())) {
            professores.salvar(professor);
        }
    }
}
