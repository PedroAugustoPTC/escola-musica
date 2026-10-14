package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageCatalogTest {

    private final MessageCatalog catalog = new MessageCatalog();

    @Test
    void everyErrorCodeHasText() {
        assertThat(
                        Arrays.stream(ErrorCode.values())
                                .map(ErrorCode::messageKey)
                                .filter(key -> !catalog.contains(key)))
                .as("códigos sem texto no catálogo")
                .isEmpty();
    }

    @Test
    void replacesPlaceholdersAndKeepsAccents() {
        assertThat(catalog.text("SAUDACAO", Map.of("nome", "Clara"))).isEqualTo("Olá, Clara");
        assertThat(catalog.text("ALTERACAO_APLICADA", Map.of("versao", 2)))
                .isEqualTo("Alteração aplicada. Confira a versão 2.");
        assertThat(catalog.text("TEXTO_ENTENDIDO", Map.of("texto", "troque $1 por \\x")))
                .isEqualTo("Entendi: “troque $1 por \\x”");
        assertThat(catalog.text("NENHUM_ALUNO_ENCONTRADO", Map.of()))
                .isEqualTo("Nenhum aluno encontrado para \"\".");
        assertThat(catalog.text(ErrorCode.RELATORIO_CANCELADO, Map.of()))
                .isEqualTo("Este relatório foi cancelado e não pode mais ser alterado.");
        assertThat(catalog.text("SECAO_VAZIA")).isEqualTo("—");
    }

    @Test
    void missingKeyFails() {
        assertThat(catalog.keys()).contains("CODIGO_NECESSARIO", "AVISO_RESULTADO_WEB_PRONTO");
        assertThatThrownBy(() -> catalog.text("NAO_EXISTE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
