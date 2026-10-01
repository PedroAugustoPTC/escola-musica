package br.com.felipeblaft.relatorios.entrada.web;

import java.util.List;

public record InicioDto(
        ProfessorResumoDto professor,
        int totalAlunos,
        List<AlunoResumoDto> alunos,
        List<RelatorioEmAbertoDto> relatoriosEmAberto,
        int limiteAudioMb) {}
