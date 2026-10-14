package br.com.felipeblaft.reports.inbound.web;

import java.time.Instant;

public record SessionDto(
        TeacherSummaryDto professor, Instant sessaoExpiraEm, boolean telegramVinculado) {}
