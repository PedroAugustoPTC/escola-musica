package br.com.felipeblaft.reports.config.log;

import java.util.Locale;
import java.util.UUID;

/**
 * Código curto de referência de uma falha interna: vai ao log e à resposta ao usuário, que o
 * informa ao suporte; nunca traz detalhe técnico. Fonte única dos adaptadores da Web e do Telegram.
 */
public final class ErrorReference {

    public static final int SIZE = 8;

    private ErrorReference() {}

    public static String newReference() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, SIZE)
                .toUpperCase(Locale.ROOT);
    }
}
