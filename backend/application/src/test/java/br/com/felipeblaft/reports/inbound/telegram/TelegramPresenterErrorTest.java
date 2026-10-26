package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.ClockPort;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Erros do núcleo no bot: duplicidade nunca mostra quem já tem o CPF ou o e-mail, e a falha interna
 * nunca sai sem a referência.
 */
class TelegramPresenterErrorTest {

    private static final String UNAVAILABLE =
            "Não foi possível usar este CPF. Procure o administrador.";

    private final MessageCatalog catalog = new MessageCatalog();
    private final TelegramPresenter presenter =
            new TelegramPresenter(catalog, new MessageFormatting(catalog, mock(ClockPort.class)));

    @Test
    void duplicateCpfAndEmailBecomeTheNeutralTextWithoutTheName() {
        for (ErrorCode code : new ErrorCode[] {ErrorCode.DUPLICATE_CPF, ErrorCode.DUPLICATE_EMAIL}) {
            String text =
                    presenter.error(1L, code, Map.of("name", "Joana Prado")).text();

            assertThat(text).as(code.name()).isEqualTo(UNAVAILABLE);
        }
    }

    @Test
    void otherCodesKeepTheirDetails() {
        assertThat(
                        presenter
                                .error(
                                        1L,
                                        ErrorCode.OPERATION_TEMPORARILY_BLOCKED,
                                        Map.of("remainingSeconds", 150))
                                .text())
                .contains("3 minuto");
    }

    @Test
    void internalErrorWithoutAReferenceGetsOneInsteadOfABlank() {
        String text = presenter.error(1L, ErrorCode.INTERNAL_ERROR, Map.of()).text();

        assertThat(text)
                .matches("Algo deu errado\\. Informe o código de referência [A-Z0-9]{8} ao suporte\\.");
    }

    @Test
    void internalErrorKeepsTheReferenceThatWasGiven() {
        String text =
                presenter.error(1L, ErrorCode.INTERNAL_ERROR, Map.of("reference", "ABCD2345")).text();

        assertThat(text).contains("ABCD2345");
    }
}
