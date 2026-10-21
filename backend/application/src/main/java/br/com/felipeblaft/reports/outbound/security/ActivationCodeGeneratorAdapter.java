package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.port.ActivationCodeGeneratorPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Código de ativação do administrador: 12 caracteres do alfabeto sem ambíguos (60 bits) com {@link
 * SecureRandom} (R-07).
 */
@Component
class ActivationCodeGeneratorAdapter implements ActivationCodeGeneratorPort {

    private static final int SIZE = ActivationCode.SIZE;

    private final SecureRandom random = new SecureRandom();

    @Override
    public ActivationCode generate() {
        StringBuilder code = new StringBuilder(SIZE);
        for (int i = 0; i < SIZE; i++) {
            code.append(ActivationCode.ALPHABET.charAt(random.nextInt(ActivationCode.ALPHABET.length())));
        }
        return ActivationCode.retry(code.toString())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "código de ativação gerado fora do formato"));
    }
}
