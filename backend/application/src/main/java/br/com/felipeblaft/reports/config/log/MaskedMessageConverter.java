package br.com.felipeblaft.reports.config.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Mensagem de log com o mesmo mascaramento do perfil JSON ({@link SensitiveDataMaskingConverter}),
 * para o perfil {@code seed}, que escreve texto no terminal.
 */
public class MaskedMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return "";
        }
        String masked = SensitiveDataMaskingConverter.maskText(message);
        return masked == null ? message : masked;
    }
}
