package br.com.felipeblaft.relatorios.casodeuso.carga;

import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.CodigoEmitido;
import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.EmissaoCodigoAcessoServico;
import java.time.Instant;
import java.util.Objects;

/**
 * Reemite o código de acesso de um professor (comando {@code emitir-codigo}). Substitui o anterior e
 * reinicia a validade; não encerra sessões nem vínculos (contracts/carga-inicial.md).
 */
public class EmitirCodigoAcessoCasoDeUso {

    private final ProfessorRepositorio professores;
    private final EmissaoCodigoAcessoServico emissao;
    private final RelogioPorta relogio;

    public EmitirCodigoAcessoCasoDeUso(
            ProfessorRepositorio professores,
            EmissaoCodigoAcessoServico emissao,
            RelogioPorta relogio) {
        this.professores = Objects.requireNonNull(professores);
        this.emissao = Objects.requireNonNull(emissao);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public CodigoEmitido executar(String email) {
        Professor professor =
                professores
                        .buscarPorEmail(Professor.normalizarEmail(email))
                        .orElseThrow(() -> new ExcecaoDominio(CodigoErro.PROFESSOR_INEXISTENTE));
        Instant agora = relogio.agora();
        CodigoAcesso codigo = emissao.emitir(professor, agora);
        professores.salvar(professor);
        return new CodigoEmitido(professor.email(), codigo.valor(), professor.codigoAcessoExpiraEm());
    }
}
