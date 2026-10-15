package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;

/** Conversão dos resultados de início e alunos para os DTOs do contrato. */
final class HomeMapper {

    private HomeMapper() {}

    static TeacherSummaryDto teacher(Teacher teacher) {
        return new TeacherSummaryDto(teacher.id(), teacher.name());
    }

    static StudentSummaryDto student(StudentSummary student) {
        return new StudentSummaryDto(
                student.id(),
                student.name(),
                student.instrument(),
                student.lastLessonAt(),
                student.hasOpenReport());
    }

    static OpenReportDto open(OpenReport r) {
        return new OpenReportDto(
                r.type().name(),
                r.reportId(),
                r.jobId(),
                r.studentId(),
                r.studentName(),
                r.status() == null ? null : r.status().name(),
                r.step() == null ? null : r.step().name(),
                r.periodKey(),
                r.originChannel().name(),
                r.updatedAt());
    }

    static HomeDto start(GetTeacherHomeUseCase.Home start, int audioLimitMb) {
        return new HomeDto(
                teacher(start.teacher()),
                start.totalStudents(),
                start.students().stream().map(HomeMapper::student).toList(),
                start.openReports().stream().map(HomeMapper::open).toList(),
                audioLimitMb);
    }

    static StudentSearchDto search(SearchStudentsUseCase.Result result) {
        return new StudentSearchDto(
                result.type().name(), result.students().stream().map(HomeMapper::student).toList());
    }
}
