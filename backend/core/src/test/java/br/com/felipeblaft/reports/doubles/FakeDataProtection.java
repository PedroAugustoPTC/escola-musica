package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.nio.charset.StandardCharsets;

/** Proteção determinística e legível para os testes (sem criptografia real). */
public class FakeDataProtection implements DataProtectionPort {

    private int sequenceToken;

    @Override
    public byte[] encryptCpf(Cpf cpf) {
        return ("cifrado:" + new StringBuilder(cpf.digits()).reverse())
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String hashCpf(Cpf cpf) {
        return "cpf:" + cpf.digits();
    }

    @Override
    public String hashAccessCode(AccessCode code) {
        return "codigo:" + code.value();
    }

    @Override
    public String originHash(Channel channel, String origin) {
        return "origem:" + channel + ":" + origin;
    }

    @Override
    public String generateSessionToken() {
        sequenceToken++;
        return "token-" + sequenceToken;
    }

    @Override
    public String sessionTokenHash(String token) {
        return "hash:" + token;
    }
}
