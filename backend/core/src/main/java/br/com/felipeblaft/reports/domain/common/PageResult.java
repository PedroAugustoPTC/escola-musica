package br.com.felipeblaft.reports.domain.common;

import java.util.List;
import java.util.function.Function;

/** Página de resultados: itens da página, posição, tamanho pedido e total geral. */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public PageResult {
        items = List.copyOf(items);
    }

    /** Recorta a página pedida de uma lista já filtrada e ordenada, em memória. */
    public static <T> PageResult<T> slice(List<T> all, PageRequest request) {
        int from = (int) Math.min(request.offset(), all.size());
        int to = (int) Math.min((long) from + request.size(), all.size());
        return new PageResult<>(all.subList(from, to), request.page(), request.size(), all.size());
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, total);
    }
}
