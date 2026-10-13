package br.com.felipeblaft.reports.saida.seguranca;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.port.AccessCodeGeneratorPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Códigos de 8 caracteres com {@link SecureRandom} (R-12). */
@Component
public class GeradorCodigoAcessoAdaptador implements AccessCodeGeneratorPort {

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public AccessCode generate() {
        StringBuilder codigo = new StringBuilder(AccessCode.SIZE);
        for (int i = 0; i < AccessCode.SIZE; i++) {
            codigo.append(
                    AccessCode.ALPHABET.charAt(aleatorio.nextInt(AccessCode.ALPHABET.length())));
        }
        return AccessCode.of(codigo.toString());
    }
}
