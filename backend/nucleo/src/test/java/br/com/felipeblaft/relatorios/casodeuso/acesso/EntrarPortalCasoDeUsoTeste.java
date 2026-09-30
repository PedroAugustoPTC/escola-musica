package br.com.felipeblaft.relatorios.casodeuso.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.dubles.CenarioAcesso;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class EntrarPortalCasoDeUsoTeste {

    private final CenarioAcesso c = new CenarioAcesso();
    private final EntrarPortalCasoDeUso casoDeUso =
            new EntrarPortalCasoDeUso(
                    c.autenticacao, c.renovacao, c.sessoes, c.vinculos, c.protecao, c.relogio, c.parametros);
    private final Professor clara = c.professor("Clara Souza", "clara@exemplo.test", "K7QM2XPA");

    private static void falhaCom(ThrowingCallable acao, CodigoErro codigo) {
        assertThatThrownBy(acao)
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(codigo);
    }

    @Test
    void codigoValidoCriaSessaoComHashDoToken() {
        EntrarPortalCasoDeUso.Resultado resultado = casoDeUso.executar(" k7qm2xpa ", "10.0.0.1");

        assertThat(resultado.professor().id()).isEqualTo(clara.id());
        assertThat(resultado.token()).isEqualTo("token-1");
        assertThat(resultado.sessao().tokenHash()).isEqualTo("hash:token-1");
        assertThat(resultado.sessao().expiraEm()).isEqualTo(c.relogio.agora().plus(Duration.ofHours(12)));
        assertThat(resultado.telegramVinculado()).isFalse();
        assertThat(resultado.toString()).doesNotContain("token-1");
        assertThat(c.sessoes.todas()).hasSize(1);
    }

    @Test
    void informaSeHaTelegramVinculado() {
        c.vinculos.salvar(VinculoTelegram.novo(clara.id(), 1L, 1L, c.relogio.agora()));
        assertThat(casoDeUso.executar("K7QM2XPA", "ip").telegramVinculado()).isTrue();
    }

    @Test
    void codigoInvalidoOuForaDoFormato() {
        falhaCom(() -> casoDeUso.executar("ZZZZZZZZ", "ip"), CodigoErro.CODIGO_INVALIDO);
        falhaCom(() -> casoDeUso.executar("curto", "ip"), CodigoErro.CODIGO_INVALIDO);
        falhaCom(() -> casoDeUso.executar(null, "ip"), CodigoErro.CODIGO_INVALIDO);
        assertThat(c.tentativasRepositorio.todas().getFirst().tentativasIncorretas()).isEqualTo(3);
        assertThat(c.sessoes.todas()).isEmpty();
    }

    @Test
    void professorInativoNaoEntra() {
        clara.atualizarCadastro(clara.nome(), false, c.relogio.agora());
        falhaCom(() -> casoDeUso.executar("K7QM2XPA", "ip"), CodigoErro.CODIGO_INVALIDO);
    }

    @Test
    void codigoVencidoAposSeteDiasSemUso() {
        c.relogio.avancar(Duration.ofDays(7));
        falhaCom(() -> casoDeUso.executar("K7QM2XPA", "ip"), CodigoErro.CODIGO_EXPIRADO);
        assertThat(c.tentativasRepositorio.todas()).isEmpty();
    }

    @Test
    void bloqueiaNaSextaTentativaMesmoComCodigoCertoEZeraNoSucesso() {
        for (int i = 0; i < 5; i++) {
            falhaCom(() -> casoDeUso.executar("ZZZZZZZZ", "ip"), CodigoErro.CODIGO_INVALIDO);
        }
        assertThatThrownBy(() -> casoDeUso.executar("K7QM2XPA", "ip"))
                .isInstanceOf(ExcecaoDominio.class)
                .satisfies(
                        e -> {
                            ExcecaoDominio erro = (ExcecaoDominio) e;
                            assertThat(erro.codigo()).isEqualTo(CodigoErro.BLOQUEADO_TEMPORARIAMENTE);
                            assertThat(erro.detalhes()).containsEntry("segundosRestantes", 900L);
                        });
        // outra origem não é afetada
        assertThat(casoDeUso.executar("K7QM2XPA", "outro-ip").professor().id()).isEqualTo(clara.id());

        c.relogio.avancar(Duration.ofMinutes(15));
        assertThat(casoDeUso.executar("K7QM2XPA", "ip").professor().id()).isEqualTo(clara.id());
        assertThat(c.tentativasRepositorio.todas())
                .allSatisfy(t -> assertThat(t.tentativasIncorretas()).isZero());
    }

    @Test
    void entrarRenovaAValidadeDoCodigo() {
        c.relogio.avancar(Duration.ofDays(6));
        casoDeUso.executar("K7QM2XPA", "ip");
        assertThat(c.professores.buscarPorId(clara.id()).orElseThrow().codigoAcessoExpiraEm())
                .isEqualTo(c.relogio.agora().plus(Duration.ofDays(7)));
    }
}
