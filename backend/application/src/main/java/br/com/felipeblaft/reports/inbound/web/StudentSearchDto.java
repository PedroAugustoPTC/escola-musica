package br.com.felipeblaft.reports.inbound.web;

import java.util.List;

/** {@code resultado} é TODOS, ENCONTRADOS ou NENHUM (que traz todos os alunos do professor). */
public record StudentSearchDto(String resultado, List<StudentSummaryDto> alunos) {}
