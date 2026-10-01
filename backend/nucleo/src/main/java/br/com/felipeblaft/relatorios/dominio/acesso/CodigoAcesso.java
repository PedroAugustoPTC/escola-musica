package br.com.felipeblaft.relatorios.dominio.acesso;

import java.util.Locale;
import java.util.Optional;

/**
 * Código de acesso do professor: 8 caracteres do alfabeto sem caracteres ambíguos (FR-004, R-12).
 * Só existe em claro na saída da carga inicial; no banco fica apenas o HMAC.
 */
public final class CodigoAcesso {

    public static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int TAMANHO = 8;

    private final String valor;

    private CodigoAcesso(String valor) {
        this.valor = valor;
    }

    /**
     * Normaliza (sem espaços nem hífens, em maiúsculas) e valida o formato; vazio se não for um
     * código possível. Aceita a forma agrupada mostrada ao professor (ex.: {@code K7QM-2XPA}).
     */
    public static Optional<CodigoAcesso> tentar(String entrada) {
        if (entrada == null) {
            return Optional.empty();
        }
        String normalizado = entrada.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (normalizado.length() != TAMANHO) {
            return Optional.empty();
        }
        for (int i = 0; i < normalizado.length(); i++) {
            if (ALFABETO.indexOf(normalizado.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new CodigoAcesso(normalizado));
    }

    /** Texto com cara de código (6 a 10 letras ou dígitos): conta como tentativa no Telegram. */
    public static boolean pareceCodigo(String entrada) {
        return entrada != null && entrada.replaceAll("[\\s-]", "").matches("[A-Za-z0-9]{6,10}");
    }

    public static CodigoAcesso de(String entrada) {
        return tentar(entrada)
                .orElseThrow(
                        () -> new IllegalArgumentException("código de acesso fora do formato"));
    }

    public String valor() {
        return valor;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof CodigoAcesso codigo && codigo.valor.equals(valor);
    }

    @Override
    public int hashCode() {
        return valor.hashCode();
    }

    @Override
    public String toString() {
        return "CodigoAcesso[***]";
    }
}
