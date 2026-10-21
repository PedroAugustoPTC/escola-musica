package br.com.felipeblaft.reports.domain.common;

import java.util.Map;

/**
 * Pedido de página das listas (data-model.md §5): página a partir de 0, tamanho de 1 a 100 (padrão
 * 20). A busca e a paginação são feitas em memória (R-22).
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw invalid("page");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw invalid("size");
        }
    }

    /** Aplica os padrões (página 0, tamanho 20) aos valores ausentes. */
    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    public static PageRequest first() {
        return new PageRequest(0, DEFAULT_SIZE);
    }

    public long offset() {
        return (long) page * size;
    }

    private static DomainException invalid(String field) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, Map.of("field", field));
    }
}
