package br.com.felipeblaft.reports.inbound.web;

import java.util.List;

public record HomeDto(
        TeacherSummaryDto professor,
        int totalAlunos,
        List<StudentSummaryDto> alunos,
        List<OpenReportDto> relatoriosEmAberto,
        int limiteAudioMb) {}
