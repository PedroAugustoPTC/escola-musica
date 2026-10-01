package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.dominio.acesso.SessaoWeb;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
import br.com.felipeblaft.relatorios.servico.RenovacaoAcessoServico;
import java.time.Instant;
import java.util.Objects;

/**
 * Valida a sessão da Web a cada requisição (FR-008): expiração deslizante e revogação real no
 * servidor. Cada uso renova a sessão e a validade do código (FR-005).
 */
public class ValidarSessaoWebCasoDeUso {

    public record Resultado(Professor professor, SessaoWeb sessao) {}

    private final SessaoWebRepositorio sessoes;
    private final ProfessorRepositorio professores;
    private final RenovacaoAcessoServico renovacao;
    private final ProtecaoDadosPorta protecao;
    private final RelogioPorta relogio;
    private final ParametrosNegocio parametros;

    public ValidarSessaoWebCasoDeUso(
            SessaoWebRepositorio sessoes,
            ProfessorRepositorio professores,
            RenovacaoAcessoServico renovacao,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio,
            ParametrosNegocio parametros) {
        this.sessoes = Objects.requireNonNull(sessoes);
        this.professores = Objects.requireNonNull(professores);
        this.renovacao = Objects.requireNonNull(renovacao);
        this.protecao = Objects.requireNonNull(protecao);
        this.relogio = Objects.requireNonNull(relogio);
        this.parametros = Objects.requireNonNull(parametros);
    }

    public Resultado executar(String token) {
        if (token == null || token.isBlank()) {
            throw new ExcecaoDominio(CodigoErro.NAO_AUTENTICADO);
        }
        Instant agora = relogio.agora();
        SessaoWeb sessao =
                sessoes.buscarPorTokenHash(protecao.hashTokenSessao(token))
                        .orElseThrow(() -> new ExcecaoDominio(CodigoErro.SESSAO_EXPIRADA));
        if (!sessao.validaEm(agora)) {
            sessoes.remover(sessao.id());
            throw new ExcecaoDominio(CodigoErro.SESSAO_EXPIRADA);
        }
        Professor professor =
                professores.buscarPorId(sessao.professorId()).filter(Professor::ativo).orElse(null);
        if (professor == null) {
            sessoes.remover(sessao.id());
            throw new ExcecaoDominio(CodigoErro.SESSAO_EXPIRADA);
        }
        if (sessao.registrarUso(agora, parametros.inatividadeSessaoWeb())) {
            sessoes.salvar(sessao);
        }
        renovacao.renovar(professor);
        return new Resultado(professor, sessao);
    }
}
