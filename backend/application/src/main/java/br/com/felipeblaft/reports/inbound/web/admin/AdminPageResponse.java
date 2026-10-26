package br.com.felipeblaft.reports.inbound.web.admin;

import java.util.List;

/** Página de uma lista da área administrativa (schemas {@code *Page} do contrato). */
public record AdminPageResponse<T>(List<T> items, int page, int size, long total) {

    /** Sem os itens: eles podem trazer dado pessoal. */
    @Override
    public String toString() {
        return "AdminPageResponse[page=" + page + ", size=" + size + ", total=" + total + "]";
    }
}
