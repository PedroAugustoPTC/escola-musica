package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Validação do código de acesso, a mesma nos dois canais (FR-004, FR-007): bloqueio verificado
 * antes do código; código inexistente, fora do formato ou de professor inativo conta como tentativa
 * incorreta; código vencido é recusado com mensagem própria. O código nunca é consumido.
 */
public class AutenticacaoCodigoServico {

    private final ControleTentativasAcessoServico tentativas;
    private final ProfessorRepositorio professores;
    private final ProtecaoDadosPorta protecao;
    private final RelogioPorta relogio;

    public AutenticacaoCodigoServico(
            ControleTentativasAcessoServico tentativas,
            ProfessorRepositorio professores,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio) {
        this.tentativas = Objects.requireNonNull(tentativas);
        this.professores = Objects.requireNonNull(professores);
        this.protecao = Objects.requireNonNull(protecao);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public Professor autenticar(Canal canal, String origem, String textoCodigo) {
        tentativas.verificarBloqueio(canal, origem);
        Instant agora = relogio.agora();
        Optional<Professor> professor =
                CodigoAcesso.tentar(textoCodigo)
                        .map(protecao::hashCodigoAcesso)
                        .flatMap(professores::buscarPorCodigoAcessoHash)
                        .filter(Professor::ativo);
        if (professor.isEmpty()) {
            tentativas.registrarFalha(canal, origem);
            throw new ExcecaoDominio(CodigoErro.CODIGO_INVALIDO);
        }
        if (!professor.get().codigoValidoEm(agora)) {
            throw new ExcecaoDominio(CodigoErro.CODIGO_EXPIRADO);
        }
        tentativas.registrarSucesso(canal, origem);
        return professor.get();
    }
}
