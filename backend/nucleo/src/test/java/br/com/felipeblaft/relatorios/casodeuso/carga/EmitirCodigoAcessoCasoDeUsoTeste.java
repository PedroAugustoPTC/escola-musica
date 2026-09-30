package br.com.felipeblaft.relatorios.casodeuso.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.CodigoEmitido;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.dubles.GeradorCodigoAcessoFixo;
import br.com.felipeblaft.relatorios.dubles.ProfessorRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.ProtecaoDadosFalsa;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import br.com.felipeblaft.relatorios.servico.EmissaoCodigoAcessoServico;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmitirCodigoAcessoCasoDeUsoTeste {

    private final RelogioFixo relogio = new RelogioFixo();
    private final ProfessorRepositorioEmMemoria professores = new ProfessorRepositorioEmMemoria();
    private final ProtecaoDadosFalsa protecao = new ProtecaoDadosFalsa();
    private final GeradorCodigoAcessoFixo gerador = new GeradorCodigoAcessoFixo();
    private final EmissaoCodigoAcessoServico emissao =
            new EmissaoCodigoAcessoServico(gerador, protecao, professores, ParametrosNegocio.padrao());
    private final EmitirCodigoAcessoCasoDeUso casoDeUso =
            new EmitirCodigoAcessoCasoDeUso(professores, emissao, relogio);

    @Test
    void reemiteSubstituindoOHashEReiniciandoAValidade() {
        Professor clara = Professor.novo("Clara", "clara@exemplo.test", true, relogio.agora());
        clara.substituirCodigo("codigo:ANTIGO22", relogio.agora(), Duration.ofDays(7));
        professores.salvar(clara);
        relogio.avancar(Duration.ofDays(9));
        gerador.programar("NEVA2345");

        CodigoEmitido emitido = casoDeUso.executar(" Clara@Exemplo.TEST ");

        assertThat(emitido.email()).isEqualTo("clara@exemplo.test");
        assertThat(emitido.codigo()).isEqualTo("NEVA2345");
        assertThat(emitido.validoAte()).isEqualTo(relogio.agora().plus(Duration.ofDays(7)));
        Professor gravado = professores.buscarPorId(clara.id()).orElseThrow();
        assertThat(gravado.codigoAcessoHash()).isEqualTo("codigo:NEVA2345");
        assertThat(gravado.codigoValidoEm(relogio.agora())).isTrue();
    }

    @Test
    void professorInexistente() {
        assertThatThrownBy(() -> casoDeUso.executar("ninguem@exemplo.test"))
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(CodigoErro.PROFESSOR_INEXISTENTE);
    }

    @Test
    void geraOutroCodigoSeOHashJaPertenceAOutroProfessor() {
        Professor bruno = Professor.novo("Bruno", "bruno@exemplo.test", true, relogio.agora());
        bruno.substituirCodigo("codigo:REPETXDA", relogio.agora(), Duration.ofDays(7));
        professores.salvar(bruno);
        Professor clara = Professor.novo("Clara", "clara@exemplo.test", true, relogio.agora());
        professores.salvar(clara);
        gerador.programar("REPETXDA", "UNXCA234");

        assertThat(casoDeUso.executar("clara@exemplo.test").codigo()).isEqualTo("UNXCA234");
    }

    @Test
    void mesmoCodigoDoProprioProfessorEhAceito() {
        Professor clara = Professor.novo("Clara", "clara@exemplo.test", true, relogio.agora());
        clara.substituirCodigo("codigo:MESMA234", relogio.agora(), Duration.ofDays(7));
        professores.salvar(clara);
        gerador.programar("MESMA234");

        assertThat(casoDeUso.executar("clara@exemplo.test").codigo()).isEqualTo("MESMA234");
    }

    @Test
    void desisteDepoisDeMuitasColisoes() {
        Professor bruno = Professor.novo("Bruno", "bruno@exemplo.test", true, relogio.agora());
        bruno.substituirCodigo("codigo:REPETXDA", relogio.agora(), Duration.ofDays(7));
        professores.salvar(bruno);
        Professor clara = Professor.novo("Clara", "clara@exemplo.test", true, relogio.agora());
        professores.salvar(clara);
        for (int i = 0; i < EmissaoCodigoAcessoServico.TENTATIVAS_UNICIDADE; i++) {
            gerador.programar("REPETXDA");
        }

        assertThatThrownBy(() -> casoDeUso.executar("clara@exemplo.test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void geradorSequencialProduzCodigosValidos() {
        Professor clara = Professor.novo("Clara", "clara@exemplo.test", true, relogio.agora());
        professores.salvar(clara);
        assertThat(casoDeUso.executar("clara@exemplo.test").codigo()).hasSize(8);
    }
}
