package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;

/** Conversão dos resultados de início e alunos para os DTOs do contrato. */
final class InicioMapeador {

    private InicioMapeador() {}

    static ProfessorResumoDto professor(Teacher professor) {
        return new ProfessorResumoDto(professor.id(), professor.name());
    }

    static AlunoResumoDto aluno(StudentSummary aluno) {
        return new AlunoResumoDto(
                aluno.id(),
                aluno.name(),
                aluno.instrument(),
                aluno.lastLessonAt(),
                aluno.hasOpenReport());
    }

    static RelatorioEmAbertoDto emAberto(OpenReport r) {
        return new RelatorioEmAbertoDto(
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

    static InicioDto inicio(GetTeacherHomeUseCase.Home inicio, int limiteAudioMb) {
        return new InicioDto(
                professor(inicio.teacher()),
                inicio.totalStudents(),
                inicio.students().stream().map(InicioMapeador::aluno).toList(),
                inicio.openReports().stream().map(InicioMapeador::emAberto).toList(),
                limiteAudioMb);
    }

    static BuscaAlunosDto busca(SearchStudentsUseCase.Result resultado) {
        return new BuscaAlunosDto(
                resultado.type().name(),
                resultado.students().stream().map(InicioMapeador::aluno).toList());
    }
}
