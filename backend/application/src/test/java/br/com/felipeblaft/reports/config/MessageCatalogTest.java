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
        assertThat(catalog.text("GREETING", Map.of("name", "Clara"))).isEqualTo("Olá, Clara");
        assertThat(catalog.text("CHANGE_APPLIED", Map.of("version", 2)))
                .isEqualTo("Alteração aplicada. Confira a versão 2.");
        assertThat(catalog.text("UNDERSTOOD_TEXT", Map.of("text", "troque $1 por \\x")))
                .isEqualTo("Entendi: “troque $1 por \\x”");
        assertThat(catalog.text("NO_STUDENT_FOUND", Map.of()))
                .isEqualTo("Nenhum aluno encontrado para \"\".");
        assertThat(catalog.text(ErrorCode.REPORT_CANCELLED, Map.of()))
                .isEqualTo("Este relatório foi cancelado e não pode mais ser alterado.");
        assertThat(catalog.text("EMPTY_SECTION")).isEqualTo("—");
    }

    @Test
    void missingKeyFails() {
        assertThat(catalog.keys()).contains("CODE_REQUIRED", "WEB_RESULT_NOTICE_READY");
        assertThatThrownBy(() -> catalog.text("DOES_NOT_EXIST"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
