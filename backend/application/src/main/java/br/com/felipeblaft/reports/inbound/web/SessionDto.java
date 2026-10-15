package br.com.felipeblaft.reports.inbound.web;

import java.time.Instant;

public record SessionDto(
        TeacherSummaryDto teacher, Instant sessionExpiresAt, boolean telegramLinked) {}
