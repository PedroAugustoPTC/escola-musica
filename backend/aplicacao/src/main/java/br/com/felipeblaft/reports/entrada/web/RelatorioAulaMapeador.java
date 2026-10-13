package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.AprovacaoDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.ConteudoRelatorioAulaDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.InteracaoDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.RelatorioAulaDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.TrabalhoDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.TrabalhoRefDto;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase.JobView;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;

/** Conversão do relatório de aula e dos trabalhos para os DTOs do contrato. */
final class RelatorioAulaMapeador {

    private RelatorioAulaMapeador() {}

    static TrabalhoRefDto referencia(ProcessingJob trabalho) {
        return new TrabalhoRefDto(trabalho.id());
    }

    static TrabalhoDto trabalho(JobView visao) {
        ProcessingJob t = visao.job();
        return new TrabalhoDto(
                t.id(),
                t.type().name(),
                t.status().name(),
                t.step().name(),
                visao.delayed(),
                t.result() == null ? null : t.result().name(),
                t.reportType() == null ? null : t.reportType().name(),
                t.reportId(),
                visao.understoodText().orElse(null));
    }

    static ConteudoRelatorioAulaDto conteudo(LessonReportContent c) {
        LessonReportContent conteudo = c == null ? LessonReportContent.empty() : c;
        return new ConteudoRelatorioAulaDto(
                conteudo.conteudosTrabalhados(),
                conteudo.evolucao(),
                conteudo.dificuldades(),
                conteudo.atividadesPropostas(),
                conteudo.observacoes());
    }

    static InteracaoDto interacao(ReportInteraction i) {
        return new InteracaoDto(
                i.type().name(), i.channel().name(), i.medium().name(), i.text(), i.createdAt());
    }

    static RelatorioAulaDto relatorio(LessonReportView visao) {
        LessonReport r = visao.report();
        String etapa =
                visao.jobInProgress()
                        .map(t -> t.step().name())
                        .orElse(r.step() == null ? null : r.step().name());
        return new RelatorioAulaDto(
                r.id(),
                r.studentId(),
                visao.studentName(),
                visao.teacherName(),
                visao.lesson().lessonDate(),
                r.status().name(),
                etapa,
                visao.jobInProgress().map(ProcessingJob::id).orElse(null),
                r.currentVersion(),
                r.originChannel().name(),
                conteudo(r.content()),
                r.pendingQuestions(),
                r.questionRounds(),
                visao.lastInteraction().map(RelatorioAulaMapeador::interacao).orElse(null),
                visao.pdfAvailable(),
                r.approvedAt(),
                r.updatedAt());
    }

    static AprovacaoDto aprovacao(ApproveLessonReportUseCase.Approval aprovacao) {
        return new AprovacaoDto(
                aprovacao.reportId(),
                "APROVADO",
                aprovacao.approvedVersion(),
                aprovacao.approvedAt());
    }
}
