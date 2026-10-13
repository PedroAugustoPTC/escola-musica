package br.com.felipeblaft.reports.port;

/**
 * Gravação recusada porque outro canal gravou antes (bloqueio otimista ou índice único parcial). O
 * caso de uso traduz para o erro de negócio correspondente.
 */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String detail) {
        super(detail, null, false, false);
    }
}
