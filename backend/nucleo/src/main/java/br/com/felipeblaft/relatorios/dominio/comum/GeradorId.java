package br.com.felipeblaft.relatorios.dominio.comum;

import java.security.SecureRandom;
import java.util.UUID;

/** Identificadores UUID versão 7 (ordenáveis pelo tempo de criação), como pede o modelo de dados. */
public final class GeradorId {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeradorId() {}

    public static UUID novo() {
        long milissegundos = System.currentTimeMillis();
        byte[] aleatorios = new byte[10];
        ALEATORIO.nextBytes(aleatorios);
        long alto = (milissegundos << 16) | 0x7000L | ((aleatorios[0] & 0x0FL) << 8) | (aleatorios[1] & 0xFFL);
        long baixo = 0x8000_0000_0000_0000L | ((aleatorios[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            baixo |= (aleatorios[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(alto, baixo);
    }
}
