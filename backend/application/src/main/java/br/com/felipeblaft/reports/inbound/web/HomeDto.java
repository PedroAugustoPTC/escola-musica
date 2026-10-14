package br.com.felipeblaft.reports.inbound.web;

import java.util.List;

public record HomeDto(
        TeacherSummaryDto teacher,
        int totalStudents,
        List<StudentSummaryDto> students,
        List<OpenReportDto> openReports,
        int audioLimitMb) {}
