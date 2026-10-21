package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash de senha determinístico e rápido para os testes, com a forma de um hash adaptativo: sal por
 * hash (a mesma senha gera hashes diferentes), parâmetros dentro do texto e verificação que só
 * confere com a senha certa. O texto do hash nunca contém a senha. Não é criptografia.
 */
public class FakePasswordHash implements PasswordHashPort {

    private static final String PREFIX = "fake-argon2id$v=";
    private static final Secret DECOY = new Secret("verification-decoy");

    private int parametersVersion = 1;
    private int hashesProduced;
    private int decoyVerifications;

    @Override
    public String hash(Secret password) {
        hashesProduced++;
        return encode(password, "salt" + hashesProduced, parametersVersion);
    }

    @Override
    public boolean matches(Secret password, String encodedHash) {
        String[] parts = parse(encodedHash);
        if (parts == null) {
            return false;
        }
        return encode(password, parts[1], Integer.parseInt(parts[0])).equals(encodedHash);
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        String[] parts = parse(encodedHash);
        return parts == null || Integer.parseInt(parts[0]) != parametersVersion;
    }

    @Override
    public void verifyAgainstDecoy(Secret password) {
        decoyVerifications++;
        matches(password, encode(DECOY, "decoy", parametersVersion));
    }

    /** Sobe os parâmetros "atuais": os hashes já guardados passam a pedir regravação. */
    public void raiseParameters() {
        parametersVersion++;
    }

    public int hashesProduced() {
        return hashesProduced;
    }

    public int decoyVerifications() {
        return decoyVerifications;
    }

    private static String encode(Secret password, String salt, int version) {
        return PREFIX + version + "$" + salt + "$" + digest(salt + ":" + password.value());
    }

    /** Devolve {versão, sal, resumo} ou {@code null} se o texto não for um hash deste dublê. */
    private static String[] parse(String encodedHash) {
        if (encodedHash == null || !encodedHash.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = encodedHash.substring(PREFIX.length()).split("\\$", -1);
        if (parts.length != 3 || !parts[0].matches("\\d+")) {
            return null;
        }
        return parts;
    }

    private static String digest(String text) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
