package br.com.felipeblaft.reports.inbound.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;

/**
 * Comparação de segredos em tempo constante (R-09): compara o SHA-256 do valor recebido com o do
 * configurado, sempre sobre digests de 32 bytes, de modo que o tempo não depende do tamanho do
 * segredo nem de quantos caracteres coincidem. Segredo não configurado (nulo ou vazio) é comparado
 * contra um digest-isca aleatório que nenhuma entrada reproduz: <em>fail-closed</em>. Com mais de
 * um segredo válido (rotação), todos são sempre avaliados, sem curto-circuito.
 */
public final class SecretComparator {

    private static final byte[] BAIT = digest("bait:" + random());

    private final Runnable onEvaluate;

    public SecretComparator() {
        this(() -> {});
    }

    /** Gancho de teste: chamado uma vez a cada segredo configurado avaliado. */
    SecretComparator(Runnable onEvaluate) {
        this.onEvaluate = onEvaluate;
    }

    /** {@code true} se o recebido for igual ao configurado; nunca para configurado vazio. */
    public boolean equal(String received, String configured) {
        return containsAny(received, List.of(value(configured)));
    }

    /**
     * {@code true} se o recebido for igual a algum dos configurados. Todos são avaliados mesmo
     * depois de um acerto.
     */
    public boolean containsAny(String received, List<String> configuredSecrets) {
        byte[] digestReceived = digest(value(received));
        boolean found = false;
        for (String configured : configuredSecrets) {
            boolean present = configured != null && !configured.isEmpty();
            byte[] digestConfigured = present ? digest(configured) : BAIT;
            onEvaluate.run();
            // sem curto-circuito: o | sempre avalia os dois lados
            found |= MessageDigest.isEqual(digestReceived, digestConfigured) & present;
        }
        return found;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static String random() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponível", error);
        }
    }
}
