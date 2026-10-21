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
        return ("encrypted:" + new StringBuilder(cpf.digits()).reverse())
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String hashCpf(Cpf cpf) {
        return "cpf:" + cpf.digits();
    }

    @Override
    public String hashAccessCode(AccessCode code) {
        return "code:" + code.value();
    }

    @Override
    public String originHash(Channel channel, String origin) {
        return hex("origin:" + channel + ":" + origin);
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

    @Override
    public String hashActivationCode(br.com.felipeblaft.reports.domain.access.ActivationCode code) {
        return "activation:" + code.value();
    }

    @Override
    public String originHash(
            br.com.felipeblaft.reports.domain.access.AttemptScope scope, String key) {
        return hex("origin:" + scope + ":" + key);
    }

    /** Resumo determinístico de 64 hexadecimais, o mesmo formato do adaptador real. */
    private static String hex(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
