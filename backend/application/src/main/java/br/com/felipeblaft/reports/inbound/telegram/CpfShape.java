package br.com.felipeblaft.reports.inbound.telegram;

import java.util.regex.Pattern;

/**
 * Reconhece apenas a <em>forma</em> de um CPF (11 dígitos, com ou sem pontos e traço) para que o
 * texto nunca seja usado como busca nem guardado; a validade do CPF é decidida no núcleo.
 */
final class CpfShape {

    private static final Pattern SHAPE = Pattern.compile("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}");

    private CpfShape() {}

    static boolean matches(String text) {
        return text != null && SHAPE.matcher(text.strip()).matches();
    }
}
