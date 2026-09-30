package br.com.felipeblaft.relatorios.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogoMensagensTeste {

    private final CatalogoMensagens catalogo = new CatalogoMensagens();

    @Test
    void todoCodigoDeErroTemTexto() {
        assertThat(
                        Arrays.stream(CodigoErro.values())
                                .map(CodigoErro::chaveMensagem)
                                .filter(chave -> !catalogo.contem(chave)))
                .as("códigos sem texto no catálogo")
                .isEmpty();
    }

    @Test
    void substituiPlaceholdersEMantemAcentos() {
        assertThat(catalogo.texto("SAUDACAO", Map.of("nome", "Clara"))).isEqualTo("Olá, Clara");
        assertThat(catalogo.texto("ALTERACAO_APLICADA", Map.of("versao", 2)))
                .isEqualTo("Alteração aplicada. Confira a versão 2.");
        assertThat(catalogo.texto("TEXTO_ENTENDIDO", Map.of("texto", "troque $1 por \\x")))
                .isEqualTo("Entendi: “troque $1 por \\x”");
        assertThat(catalogo.texto("NENHUM_ALUNO_ENCONTRADO", Map.of()))
                .isEqualTo("Nenhum aluno encontrado para \"\".");
        assertThat(catalogo.texto(CodigoErro.RELATORIO_CANCELADO, Map.of()))
                .isEqualTo("Este relatório foi cancelado e não pode mais ser alterado.");
        assertThat(catalogo.texto("SECAO_VAZIA")).isEqualTo("—");
    }

    @Test
    void chaveInexistenteFalha() {
        assertThat(catalogo.chaves()).contains("CODIGO_NECESSARIO", "AVISO_RESULTADO_WEB_PRONTO");
        assertThatThrownBy(() -> catalogo.texto("NAO_EXISTE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
