package br.com.felipeblaft.reports.inbound.web;

import java.time.Instant;
import java.util.UUID;

public record OpenReportDto(
        String type,
        UUID reportId,
        UUID jobId,
        UUID studentId,
        String studentName,
        String status,
        String step,
        String periodKey,
        String originChannel,
        Instant updatedAt) {}
