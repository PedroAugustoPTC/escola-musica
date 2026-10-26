package br.com.felipeblaft.reports.inbound.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Respostas escritas pelos filtros de segurança, fora do MVC: o aviso de falha interna nunca sai
 * com o marcador {@code {reference}} nem com a referência vazia.
 */
class SecurityResponsesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final MessageCatalog catalog = new MessageCatalog();

    private JsonNode write(ErrorCode code, int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityResponses.write(response, code, status, catalog);
        assertThat(response.getStatus()).isEqualTo(status);
        return JSON.readTree(response.getContentAsString());
    }

    @Test
    void internalErrorCarriesAReferenceInTheTextAndInTheBody() throws Exception {
        JsonNode body = write(ErrorCode.INTERNAL_ERROR, 500);

        String reference = body.path("reference").asString();
        assertThat(reference).matches("[A-Z0-9]{8}");
        assertThat(body.path("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.path("message").asString())
                .isEqualTo(
                        "Algo deu errado. Informe o código de referência "
                                + reference
                                + " ao suporte.")
                .doesNotContain("{reference}");
    }

    @Test
    void eachInternalErrorGetsItsOwnReference() throws Exception {
        assertThat(write(ErrorCode.INTERNAL_ERROR, 500).path("reference").asString())
                .isNotEqualTo(write(ErrorCode.INTERNAL_ERROR, 500).path("reference").asString());
    }

    @Test
    void otherCodesHaveNoReference() throws Exception {
        JsonNode body = write(ErrorCode.RESOURCE_NOT_FOUND, 404);

        assertThat(body.has("reference")).isFalse();
        assertThat(body.path("message").asString()).isEqualTo("Não encontramos o que você procurou.");
    }
}
