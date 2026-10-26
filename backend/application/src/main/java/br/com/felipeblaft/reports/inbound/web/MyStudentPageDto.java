package br.com.felipeblaft.reports.inbound.web;

import java.util.List;

/** Página de alunos do professor (schema {@code StudentPage}). */
public record MyStudentPageDto(List<MyStudentDto> items, int page, int size, long total) {

    /** Sem os itens: eles trazem dado pessoal. */
    @Override
    public String toString() {
        return "MyStudentPageDto[page=" + page + ", size=" + size + ", total=" + total + "]";
    }
}
