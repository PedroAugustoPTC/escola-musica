package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase.Preview;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gerar o semestral (FR-042, FR-046, FR-034): confere as pré-condições na ordem de data-model.md §9
 * e enfileira a consolidação. Sem aulas aprovadas, nada é criado e a IA não é chamada.
 */
public class GenerateSemesterReportUseCase {

    private final GetSemesterPreviewUseCase preview;
    private final JobQueuePort queue;
    private final ClockPort clock;

    public GenerateSemesterReportUseCase(
            GetSemesterPreviewUseCase preview, JobQueuePort queue, ClockPort clock) {
        this.preview = Objects.requireNonNull(preview);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProcessingJob run(
            UUID teacherId,
            UUID studentId,
            String periodKey,
            boolean confirmEvenWithOpen,
            Channel channel) {
        Preview current = preview.run(teacherId, studentId, periodKey);
        requireCanGenerate(current, confirmEvenWithOpen);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.CONSOLIDACAO_SEMESTRAL,
                        teacherId,
                        studentId,
                        ReportType.SEMESTRAL,
                        null,
                        channel,
                        Medium.TEXTO,
                        JobStep.CONSOLIDANDO,
                        clock.now());
        job.defineParameter(ProcessingJob.PERIOD_KEY, current.period().key());
        if (confirmEvenWithOpen) {
            job.defineParameter(ProcessingJob.CONFIRM_OPEN, "true");
        }
        try {
            queue.enqueue(job);
        } catch (ConcurrencyConflictException conflict) {
            requireCanGenerate(preview.run(teacherId, studentId, periodKey), true);
            throw new DomainException(ErrorCode.RELATORIO_EM_ABERTO);
        }
        return job;
    }

    static void requireCanGenerate(Preview preview, boolean confirmEvenWithOpen) {
        if (preview.approvedSemesterId().isPresent()) {
            throw new DomainException(ErrorCode.SEMESTRAL_JA_APROVADO);
        }
        if (preview.openSemester().isPresent()) {
            throw new DomainException(
                    ErrorCode.RELATORIO_EM_ABERTO,
                    Map.of("relatorioEmAberto", preview.openSemester().get().details()));
        }
        if (preview.approvedLessons() == 0) {
            throw new DomainException(ErrorCode.SEM_AULAS_APROVADAS_NO_PERIODO);
        }
        if (preview.openLessonReports() > 0 && !confirmEvenWithOpen) {
            throw new DomainException(
                    ErrorCode.HA_RELATORIOS_EM_ABERTO,
                    Map.of("quantidade", preview.openLessonReports()));
        }
    }
}
