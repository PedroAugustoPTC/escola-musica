package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.porta.GeradorCodigoAcessoPorta;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import java.time.Instant;
import java.util.Objects;

/**
 * Gera um código novo e único para o professor, guarda só o hash e reinicia a validade (R-12). O
 * código em claro é devolvido apenas a quem chamou, para a saída do terminal.
 */
public class EmissaoCodigoAcessoServico {

    public static final int TENTATIVAS_UNICIDADE = 10;

    private final GeradorCodigoAcessoPorta gerador;
    private final ProtecaoDadosPorta protecao;
    private final ProfessorRepositorio professores;
    private final ParametrosNegocio parametros;

    public EmissaoCodigoAcessoServico(
            GeradorCodigoAcessoPorta gerador,
            ProtecaoDadosPorta protecao,
            ProfessorRepositorio professores,
            ParametrosNegocio parametros) {
        this.gerador = Objects.requireNonNull(gerador);
        this.protecao = Objects.requireNonNull(protecao);
        this.professores = Objects.requireNonNull(professores);
        this.parametros = Objects.requireNonNull(parametros);
    }

    /** Substitui o código do professor (sem gravar) e devolve o novo código em claro. */
    public CodigoAcesso emitir(Professor professor, Instant agora) {
        for (int tentativa = 0; tentativa < TENTATIVAS_UNICIDADE; tentativa++) {
            CodigoAcesso codigo = gerador.gerar();
            String hash = protecao.hashCodigoAcesso(codigo);
            boolean emUso =
                    professores
                            .buscarPorCodigoAcessoHash(hash)
                            .filter(outro -> !outro.id().equals(professor.id()))
                            .isPresent();
            if (!emUso) {
                professor.substituirCodigo(hash, agora, parametros.validadeCodigo());
                return codigo;
            }
        }
        throw new IllegalStateException("não foi possível gerar um código de acesso único");
    }
}
