package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.servico.AutenticacaoCodigoServico;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import br.com.felipeblaft.relatorios.servico.ControleTentativasAcessoServico;
import br.com.felipeblaft.relatorios.servico.RenovacaoAcessoServico;
import java.time.LocalDate;

/** Monta repositórios em memória e serviços de acesso compartilhados pelos testes. */
public class CenarioAcesso {

    public final RelogioFixo relogio = new RelogioFixo();
    public final ParametrosNegocio parametros = ParametrosNegocio.padrao();
    public final ProtecaoDadosFalsa protecao = new ProtecaoDadosFalsa();
    public final ProfessorRepositorioEmMemoria professores = new ProfessorRepositorioEmMemoria();
    public final AssociacaoRepositorioEmMemoria associacoes = new AssociacaoRepositorioEmMemoria();
    public final AlunoRepositorioEmMemoria alunos = new AlunoRepositorioEmMemoria(associacoes);
    public final SessaoWebRepositorioEmMemoria sessoes = new SessaoWebRepositorioEmMemoria();
    public final VinculoTelegramRepositorioEmMemoria vinculos =
            new VinculoTelegramRepositorioEmMemoria();
    public final TentativaAcessoRepositorioEmMemoria tentativasRepositorio =
            new TentativaAcessoRepositorioEmMemoria();
    public final RelatoriosEmAbertoConsultaFalsa emAberto = new RelatoriosEmAbertoConsultaFalsa();

    public final ControleTentativasAcessoServico tentativas =
            new ControleTentativasAcessoServico(
                    tentativasRepositorio, protecao, relogio, parametros);
    public final RenovacaoAcessoServico renovacao =
            new RenovacaoAcessoServico(professores, relogio, parametros);
    public final AutenticacaoCodigoServico autenticacao =
            new AutenticacaoCodigoServico(tentativas, professores, protecao, relogio);
    public final AutorizacaoServico autorizacao = new AutorizacaoServico(associacoes);

    /** Professor ativo com o código informado, válido por 7 dias a partir de agora. */
    public Professor professor(String nome, String email, String codigo) {
        Professor professor = Professor.novo(nome, email, true, relogio.agora());
        professor.substituirCodigo(
                protecao.hashCodigoAcesso(CodigoAcesso.de(codigo)),
                relogio.agora(),
                parametros.validadeCodigo());
        professores.salvar(professor);
        return professor;
    }

    /** Aluno associado ao professor. */
    public Aluno aluno(Professor professor, String nome, String instrumento) {
        Aluno aluno =
                Aluno.novo(
                        nome,
                        LocalDate.of(2012, 1, 1),
                        new byte[] {1},
                        "cpf:" + nome,
                        "Responsável",
                        instrumento,
                        relogio.agora());
        alunos.salvar(aluno);
        associacoes.associar(professor.id(), aluno.id());
        return aluno;
    }
}
