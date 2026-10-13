package br.com.felipeblaft.reports.saida.seguranca;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * CPF com AES-256-GCM (IV aleatório de 12 bytes prefixado) e hashes HMAC-SHA-256 com o segredo do
 * servidor (Princípio IV, R-12, R-14). Cada uso do HMAC leva um prefixo de contexto próprio, para
 * que um hash de uma finalidade nunca coincida com o de outra.
 */
@Component
public class ProtecaoDadosAdaptador implements DataProtectionPort {

    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_TOKEN = 32;

    private final SecretKeySpec chaveCpf;
    private final SecretKeySpec chaveHmac;
    private final SecureRandom aleatorio = new SecureRandom();

    public ProtecaoDadosAdaptador(PropriedadesAplicacao propriedades) {
        byte[] chave = decodificarChave(propriedades.seguranca().cpfChave());
        byte[] segredo = segredoHmac(propriedades.seguranca().segredoHmac());
        this.chaveCpf = new SecretKeySpec(chave, "AES");
        this.chaveHmac = new SecretKeySpec(segredo, "HmacSHA256");
    }

    private static byte[] decodificarChave(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("CPF_CHAVE_CIFRAGEM não configurada");
        }
        byte[] chave = Base64.getDecoder().decode(base64.strip());
        if (chave.length != 32) {
            throw new IllegalStateException("CPF_CHAVE_CIFRAGEM deve ter 32 bytes em Base64");
        }
        return chave;
    }

    private static byte[] segredoHmac(String segredo) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("SEGREDO_HMAC deve ter pelo menos 32 bytes");
        }
        return segredo.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] encryptCpf(Cpf cpf) {
        try {
            byte[] iv = new byte[TAMANHO_IV];
            aleatorio.nextBytes(iv);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chaveCpf, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cifra.doFinal(cpf.digits().getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("falha ao cifrar", erro);
        }
    }

    /** Uso restrito a testes e auditoria: o sistema nunca exibe o CPF. */
    String decifrarCpf(byte[] dados) {
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(
                    Cipher.DECRYPT_MODE,
                    chaveCpf,
                    new GCMParameterSpec(TAMANHO_TAG_BITS, dados, 0, TAMANHO_IV));
            return new String(
                    cifra.doFinal(dados, TAMANHO_IV, dados.length - TAMANHO_IV),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("falha ao decifrar", erro);
        }
    }

    @Override
    public String hashCpf(Cpf cpf) {
        return hmac("cpf:" + cpf.digits());
    }

    @Override
    public String hashAccessCode(AccessCode codigo) {
        return hmac("codigo:" + codigo.value());
    }

    @Override
    public String originHash(Channel canal, String origem) {
        return hmac("origem:" + canal.name() + ":" + origem);
    }

    @Override
    public String generateSessionToken() {
        byte[] token = new byte[TAMANHO_TOKEN];
        aleatorio.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    @Override
    public String sessionTokenHash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException(erro);
        }
    }

    private String hmac(String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHmac);
            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException(erro);
        }
    }
}
