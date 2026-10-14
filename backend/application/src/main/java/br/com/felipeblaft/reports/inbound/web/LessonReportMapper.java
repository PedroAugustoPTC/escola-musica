package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.ApprovalDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.InteractionDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.JobDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.JobRefDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.LessonReportContentDto;
import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.LessonReportDto;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase.JobView;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;

/** Conversão do relatório de aula e dos trabalhos para os DTOs do contrato. */
final class LessonReportMapper {

    private LessonReportMapper() {}

    static JobRefDto reference(ProcessingJob job) {
        return new JobRefDto(job.id());
    }

    static JobDto job(JobView view) {
        ProcessingJob t = view.job();
        return new JobDto(
                t.id(),
                t.type().name(),
                t.status().name(),
                t.step().name(),
                view.delayed(),
                t.result() == null ? null : t.result().name(),
                t.reportType() == null ? null : t.reportType().name(),
                t.reportId(),
                view.understoodText().orElse(null));
    }

    static LessonReportContentDto content(LessonReportContent c) {
        LessonReportContent content = c == null ? LessonReportContent.empty() : c;
        return new LessonReportContentDto(
                content.workedTopics(),
                content.progress(),
                content.difficulties(),
                content.proposedActivities(),
                content.observations());
    }

    static InteractionDto interaction(ReportInteraction i) {
        return new InteractionDto(
                i.type().name(), i.channel().name(), i.medium().name(), i.text(), i.createdAt());
    }

    static LessonReportDto report(LessonReportView view) {
        LessonReport r = view.report();
        String step =
                view.jobInProgress()
                        .map(t -> t.step().name())
                        .orElse(r.step() == null ? null : r.step().name());
        return new LessonReportDto(
                r.id(),
                r.studentId(),
                view.studentName(),
                view.teacherName(),
                view.lesson().lessonDate(),
                r.status().name(),
                step,
                view.jobInProgress().map(ProcessingJob::id).orElse(null),
                r.currentVersion(),
                r.originChannel().name(),
                content(r.content()),
                r.pendingQuestions(),
                r.questionRounds(),
                view.lastInteraction().map(LessonReportMapper::interaction).orElse(null),
                view.pdfAvailable(),
                r.approvedAt(),
                r.updatedAt());
    }

    static ApprovalDto approval(ApproveLessonReportUseCase.Approval approval) {
        return new ApprovalDto(
                approval.reportId(), "APROVADO", approval.approvedVersion(), approval.approvedAt());
    }
}
