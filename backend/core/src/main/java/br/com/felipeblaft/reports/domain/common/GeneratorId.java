package br.com.felipeblaft.reports.domain.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Identificadores UUID versão 7 (ordenáveis pelo tempo de criação), como pede o modelo de dados.
 */
public final class GeneratorId {

    private static final SecureRandom RANDOM = new SecureRandom();

    private GeneratorId() {}

    public static UUID create() {
        long milliseconds = System.currentTimeMillis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        long high =
                (milliseconds << 16) | 0x7000L | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
        long low = 0x8000_0000_0000_0000L | ((random[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            low |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(high, low);
    }
}
