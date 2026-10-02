package br.com.felipeblaft.relatorios.entrada.interno.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;

/**
 * Comparação de segredos em tempo constante (R-09): compara o SHA-256 do valor recebido com o do
 * configurado, sempre sobre digests de 32 bytes, de modo que o tempo não depende do tamanho do
 * segredo nem de quantos caracteres coincidem. Segredo não configurado (nulo ou vazio) é comparado
 * contra um digest-isca aleatório que nenhuma entrada reproduz: <em>fail-closed</em>. Com mais de um
 * segredo válido (rotação), todos são sempre avaliados, sem curto-circuito.
 */
public final class ComparadorSegredo {

    private static final byte[] ISCA = digest("isca:" + aleatorio());

    private final Runnable aoAvaliar;

    public ComparadorSegredo() {
        this(() -> {});
    }

    /** Gancho de teste: chamado uma vez a cada segredo configurado avaliado. */
    ComparadorSegredo(Runnable aoAvaliar) {
        this.aoAvaliar = aoAvaliar;
    }

    /** {@code true} se o recebido for igual ao configurado; nunca para configurado vazio. */
    public boolean igual(String recebido, String configurado) {
        return contemAlgum(recebido, List.of(valor(configurado)));
    }

    /**
     * {@code true} se o recebido for igual a algum dos configurados. Todos são avaliados mesmo depois
     * de um acerto.
     */
    public boolean contemAlgum(String recebido, List<String> configurados) {
        byte[] digestRecebido = digest(valor(recebido));
        boolean encontrado = false;
        for (String configurado : configurados) {
            boolean presente = configurado != null && !configurado.isEmpty();
            byte[] digestConfigurado = presente ? digest(configurado) : ISCA;
            aoAvaliar.run();
            // sem curto-circuito: o | sempre avalia os dois lados
            encontrado |= MessageDigest.isEqual(digestRecebido, digestConfigurado) & presente;
        }
        return encontrado;
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto;
    }

    private static String aleatorio() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static byte[] digest(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponível", erro);
        }
    }
}
