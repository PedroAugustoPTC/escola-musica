package br.com.felipeblaft.relatorios.casodeuso.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.telegram.FluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.dubles.CenarioAcesso;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Vincular, identificar, atualizar a conversa e encerrar o vínculo do Telegram. */
class VincularTelegramCasoDeUsoTeste {

    private final CenarioAcesso c = new CenarioAcesso();
    private final VincularTelegramCasoDeUso vincular =
            new VincularTelegramCasoDeUso(c.autenticacao, c.renovacao, c.vinculos, c.relogio);
    private final IdentificarProfessorTelegramCasoDeUso identificar =
            new IdentificarProfessorTelegramCasoDeUso(c.vinculos, c.professores, c.renovacao);
    private final EncerrarVinculoTelegramCasoDeUso encerrar =
            new EncerrarVinculoTelegramCasoDeUso(c.vinculos);
    private final AtualizarConversaTelegramCasoDeUso atualizar =
            new AtualizarConversaTelegramCasoDeUso(c.vinculos, c.relogio);
    private final Professor clara = c.professor("Clara Souza", "clara@exemplo.test", "K7QM2XPA");

    private static void falhaCom(ThrowingCallable acao, CodigoErro codigo) {
        assertThatThrownBy(acao)
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(codigo);
    }

    @Test
    void vinculaComOMesmoCodigoSemConsumir() {
        ConsultarVinculoTelegramCasoDeUso consultar =
                new ConsultarVinculoTelegramCasoDeUso(c.vinculos);
        assertThat(consultar.executar(clara.id())).isFalse();
        VincularTelegramCasoDeUso.Resultado resultado = vincular.executar(111L, 999L, "k7qm2xpa");
        assertThat(consultar.executar(clara.id())).isTrue();

        assertThat(resultado.professor().id()).isEqualTo(clara.id());
        assertThat(resultado.vinculo().chatId()).isEqualTo(999L);
        assertThat(c.vinculos.buscarPorTelegramUsuarioId(111L)).isPresent();
        assertThat(clara.codigoAcessoHash()).isEqualTo("codigo:K7QM2XPA");
    }

    @Test
    void contaJaVinculadaOuProfessorJaVinculado() {
        Professor bruno = c.professor("Bruno Lima", "bruno@exemplo.test", "BRUN2XPA");
        vincular.executar(111L, 1L, "K7QM2XPA");
        falhaCom(() -> vincular.executar(111L, 1L, "BRUN2XPA"), CodigoErro.TELEGRAM_JA_VINCULADO);
        falhaCom(() -> vincular.executar(222L, 2L, "K7QM2XPA"), CodigoErro.PROFESSOR_JA_VINCULADO);
        assertThat(c.vinculos.buscarPorProfessorId(bruno.id())).isEmpty();
    }

    @Test
    void verificaBloqueioAntesDoCodigoECodigoVencido() {
        for (int i = 0; i < 5; i++) {
            falhaCom(() -> vincular.executar(111L, 1L, "ERRADO22"), CodigoErro.CODIGO_INVALIDO);
        }
        falhaCom(
                () -> vincular.executar(111L, 1L, "K7QM2XPA"),
                CodigoErro.BLOQUEADO_TEMPORARIAMENTE);
        c.relogio.avancar(Duration.ofDays(8));
        falhaCom(() -> vincular.executar(111L, 1L, "K7QM2XPA"), CodigoErro.CODIGO_EXPIRADO);
    }

    @Test
    void professorInativoNaoVincula() {
        clara.atualizarCadastro(clara.nome(), false, c.relogio.agora());
        falhaCom(() -> vincular.executar(111L, 1L, "K7QM2XPA"), CodigoErro.CODIGO_INVALIDO);
    }

    @Test
    void identificaERenovaOCodigo() {
        vincular.executar(111L, 1L, "K7QM2XPA");
        c.relogio.avancar(Duration.ofDays(6));

        var identificacao = identificar.executar(111L).orElseThrow();

        assertThat(identificacao.professor().id()).isEqualTo(clara.id());
        assertThat(identificacao.vinculo().telegramUsuarioId()).isEqualTo(111L);
        assertThat(c.professores.buscarPorId(clara.id()).orElseThrow().codigoAcessoExpiraEm())
                .isEqualTo(c.relogio.agora().plus(Duration.ofDays(7)));
        assertThat(identificar.executar(222L)).isEmpty();
    }

    @Test
    void professorDesativadoPerdeOVinculo() {
        vincular.executar(111L, 1L, "K7QM2XPA");
        clara.atualizarCadastro(clara.nome(), false, c.relogio.agora());
        assertThat(identificar.executar(111L)).isEmpty();
        assertThat(c.vinculos.todos()).isEmpty();
    }

    @Test
    void atualizaConversaEEncerraVinculo() {
        VinculoTelegram vinculo = vincular.executar(111L, 1L, "K7QM2XPA").vinculo();
        vinculo.conversa().iniciarFluxo(FluxoConversa.HISTORICO, vinculo.conversa().etapa());
        c.relogio.avancar(Duration.ofMinutes(1));
        atualizar.executar(vinculo);
        assertThat(c.vinculos.buscarPorTelegramUsuarioId(111L).orElseThrow().atualizadoEm())
                .isEqualTo(c.relogio.agora());

        encerrar.executar(111L);
        encerrar.executar(222L);
        assertThat(c.vinculos.todos()).isEmpty();
        assertThat(identificar.executar(111L)).isEmpty();
    }
}
