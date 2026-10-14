package br.com.felipeblaft.reports.inbound.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs do relatório de aula e dos trabalhos (contracts/api-portal.openapi.yaml). */
public final class LessonReportDtos {

    private LessonReportDtos() {}

    public record JobRefDto(UUID jobId) {}

    public record JobDto(
            UUID jobId,
            String type,
            String status,
            String step,
            boolean delayed,
            String resultCode,
            String reportType,
            UUID reportId,
            String understoodText) {}

    public record LessonReportContentDto(
            List<String> workedTopics,
            String progress,
            List<String> difficulties,
            List<String> proposedActivities,
            String observations) {}

    public record InteractionDto(
            String type, String channel, String medium, String text, Instant createdAt) {}

    public record LessonReportDto(
            UUID reportId,
            UUID studentId,
            String studentName,
            String teacherName,
            LocalDate lessonDate,
            String status,
            String step,
            UUID activeJobId,
            int currentVersion,
            String originChannel,
            LessonReportContentDto content,
            List<String> pendingQuestions,
            int questionRounds,
            InteractionDto lastInteraction,
            boolean pdfAvailable,
            Instant approvedAt,
            Instant updatedAt) {}

    public record TextAnswerRequest(@Size(max = 4000) String text) {}

    public record ChangeTextRequest(
            @Size(max = 4000) String text, @NotNull @Min(1) Integer baseVersion) {}

    public record ApprovalRequest(@NotNull @Min(1) Integer confirmedVersion) {}

    public record ApprovalDto(
            UUID reportId, String status, int approvedVersion, Instant approvedAt) {}
}
