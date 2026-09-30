package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.dominio.acesso.SessaoWeb;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import br.com.felipeblaft.relatorios.servico.AutenticacaoCodigoServico;
import br.com.felipeblaft.relatorios.servico.RenovacaoAcessoServico;
import java.util.Objects;

/** Entrar no portal com o código de acesso (FR-004, FR-007, FR-008). */
public class EntrarPortalCasoDeUso {

    /** O token em claro só existe aqui, para ir ao cookie; no banco fica o hash. */
    public record Resultado(
            String token, SessaoWeb sessao, Professor professor, boolean telegramVinculado) {

        @Override
        public String toString() {
            return "Resultado[sessao=" + sessao.id() + "]";
        }
    }

    private final AutenticacaoCodigoServico autenticacao;
    private final RenovacaoAcessoServico renovacao;
    private final SessaoWebRepositorio sessoes;
    private final VinculoTelegramRepositorio vinculos;
    private final ProtecaoDadosPorta protecao;
    private final RelogioPorta relogio;
    private final ParametrosNegocio parametros;

    public EntrarPortalCasoDeUso(
            AutenticacaoCodigoServico autenticacao,
            RenovacaoAcessoServico renovacao,
            SessaoWebRepositorio sessoes,
            VinculoTelegramRepositorio vinculos,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio,
            ParametrosNegocio parametros) {
        this.autenticacao = Objects.requireNonNull(autenticacao);
        this.renovacao = Objects.requireNonNull(renovacao);
        this.sessoes = Objects.requireNonNull(sessoes);
        this.vinculos = Objects.requireNonNull(vinculos);
        this.protecao = Objects.requireNonNull(protecao);
        this.relogio = Objects.requireNonNull(relogio);
        this.parametros = Objects.requireNonNull(parametros);
    }

    /** {@code origem} é o endereço de quem tenta entrar (só o hash é guardado). */
    public Resultado executar(String codigo, String origem) {
        Professor professor = autenticacao.autenticar(Canal.WEB, origem, codigo);
        renovacao.renovar(professor);
        String token = protecao.gerarTokenSessao();
        SessaoWeb sessao =
                SessaoWeb.nova(
                        professor.id(),
                        protecao.hashTokenSessao(token),
                        relogio.agora(),
                        parametros.inatividadeSessaoWeb());
        sessoes.salvar(sessao);
        boolean telegram = vinculos.buscarPorProfessorId(professor.id()).isPresent();
        return new Resultado(token, sessao, professor, telegram);
    }
}
