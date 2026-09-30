package br.com.felipeblaft.relatorios.casodeuso.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dubles.CenarioAcesso;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** ValidarSessaoWebCasoDeUso e EncerrarSessaoWebCasoDeUso. */
class SessaoWebCasosDeUsoTeste {

    private final CenarioAcesso c = new CenarioAcesso();
    private final EntrarPortalCasoDeUso entrar =
            new EntrarPortalCasoDeUso(
                    c.autenticacao, c.renovacao, c.sessoes, c.vinculos, c.protecao, c.relogio, c.parametros);
    private final ValidarSessaoWebCasoDeUso validar =
            new ValidarSessaoWebCasoDeUso(
                    c.sessoes, c.professores, c.renovacao, c.protecao, c.relogio, c.parametros);
    private final EncerrarSessaoWebCasoDeUso encerrar =
            new EncerrarSessaoWebCasoDeUso(c.sessoes, c.protecao);
    private final Professor clara = c.professor("Clara Souza", "clara@exemplo.test", "K7QM2XPA");

    private static void falhaCom(ThrowingCallable acao, CodigoErro codigo) {
        assertThatThrownBy(acao)
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(codigo);
    }

    @Test
    void sessaoValidaIdentificaOProfessorERenovaDeslizando() {
        String token = entrar.executar("K7QM2XPA", "ip").token();
        c.relogio.avancar(Duration.ofHours(11));

        ValidarSessaoWebCasoDeUso.Resultado resultado = validar.executar(token);

        assertThat(resultado.professor().id()).isEqualTo(clara.id());
        assertThat(resultado.sessao().expiraEm()).isEqualTo(c.relogio.agora().plus(Duration.ofHours(12)));
        c.relogio.avancar(Duration.ofHours(11));
        assertThat(validar.executar(token).professor().id()).isEqualTo(clara.id());
    }

    @Test
    void usoComSessaoRenovaOCodigoEOVencimentoDoCodigoNaoEncerraASessao() {
        String token = entrar.executar("K7QM2XPA", "ip").token();
        for (int uso = 0; uso < 20; uso++) {
            c.relogio.avancar(Duration.ofHours(11));
            validar.executar(token);
        }
        Professor lido = c.professores.buscarPorId(clara.id()).orElseThrow();
        assertThat(lido.codigoValidoEm(c.relogio.agora())).isTrue();
    }

    @Test
    void semTokenNaoAutenticado() {
        falhaCom(() -> validar.executar(null), CodigoErro.NAO_AUTENTICADO);
        falhaCom(() -> validar.executar(" "), CodigoErro.NAO_AUTENTICADO);
    }

    @Test
    void tokenDesconhecidoOuExpirado() {
        falhaCom(() -> validar.executar("inventado"), CodigoErro.SESSAO_EXPIRADA);
        String token = entrar.executar("K7QM2XPA", "ip").token();
        c.relogio.avancar(Duration.ofHours(12));
        falhaCom(() -> validar.executar(token), CodigoErro.SESSAO_EXPIRADA);
        assertThat(c.sessoes.todas()).isEmpty();
    }

    @Test
    void professorDesativadoPerdeASessao() {
        String token = entrar.executar("K7QM2XPA", "ip").token();
        clara.atualizarCadastro(clara.nome(), false, c.relogio.agora());
        falhaCom(() -> validar.executar(token), CodigoErro.SESSAO_EXPIRADA);
        assertThat(c.sessoes.todas()).isEmpty();
    }

    @Test
    void encerrarRevogaNoServidor() {
        String token = entrar.executar("K7QM2XPA", "ip").token();
        encerrar.executar(token);
        falhaCom(() -> validar.executar(token), CodigoErro.SESSAO_EXPIRADA);
        encerrar.executar(null);
        encerrar.executar("");
        encerrar.executar("desconhecido");
        assertThat(c.sessoes.todas()).isEmpty();
    }
}
