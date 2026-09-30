package br.com.felipeblaft.relatorios.servico;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dubles.AssociacaoRepositorioEmMemoria;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutorizacaoServicoTeste {

    private final AssociacaoRepositorioEmMemoria associacoes = new AssociacaoRepositorioEmMemoria();
    private final AutorizacaoServico servico = new AutorizacaoServico(associacoes);
    private final UUID clara = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID matteo = UUID.randomUUID();

    @Test
    void alunoAssociadoPassa() {
        associacoes.associar(clara, matteo);
        assertThatCode(() -> servico.exigirAssociacao(clara, matteo)).doesNotThrowAnyException();
    }

    @Test
    void alunoDeOutroProfessorEhNegado() {
        associacoes.associar(bruno, matteo);
        assertThatThrownBy(() -> servico.exigirAssociacao(clara, matteo))
                .isInstanceOf(ExcecaoDominio.class)
                .hasMessage(CodigoErro.ALUNO_NAO_ASSOCIADO.name());
    }

    @Test
    void recursoDeOutroProfessorEhInexistente() {
        assertThatCode(() -> servico.exigirDono(clara, clara)).doesNotThrowAnyException();
        assertThatThrownBy(() -> servico.exigirDono(clara, bruno))
                .isInstanceOf(ExcecaoDominio.class)
                .hasMessage(CodigoErro.RECURSO_NAO_ENCONTRADO.name());
    }
}
