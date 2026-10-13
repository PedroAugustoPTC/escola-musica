package br.com.felipeblaft.reports.entrada.web;

import java.util.List;

/** {@code resultado} é TODOS, ENCONTRADOS ou NENHUM (que traz todos os alunos do professor). */
public record BuscaAlunosDto(String resultado, List<AlunoResumoDto> alunos) {}
